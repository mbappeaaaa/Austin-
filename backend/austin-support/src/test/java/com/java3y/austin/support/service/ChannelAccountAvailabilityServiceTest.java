package com.java3y.austin.support.service;

import com.java3y.austin.common.constant.CommonConstant;
import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.support.dao.ChannelAccountDao;
import com.java3y.austin.support.domain.ChannelAccount;
import com.java3y.austin.support.domain.ChannelAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelAccountAvailabilityServiceTest {

    private final ChannelAccountDao dao = mock(ChannelAccountDao.class);

    @Test
    void shouldAllowMockInDevelopmentAndRejectItInProduction() {
        ChannelAccount account = smsAccount("{\"supplierId\":0,\"supplierName\":\"Mock短信\",\"scriptName\":\"MockSmsScript\"}");

        ChannelAvailability dev = new ChannelAccountAvailabilityService(dao,
                new MockEnvironment().withProperty("spring.profiles.active", "dev"), null, null, null)
                .evaluate(account);
        MockEnvironment productionEnvironment = new MockEnvironment();
        productionEnvironment.setActiveProfiles("prod");
        ChannelAvailability prod = new ChannelAccountAvailabilityService(dao, productionEnvironment, null, null, null).evaluate(account);

        assertEquals(ChannelAvailabilityStatus.READY, dev.getStatus());
        assertEquals(ChannelAvailabilityStatus.NOT_CONFIGURED, prod.getStatus());
        assertTrue(prod.getMessage().contains("生产环境禁止"));
    }

    @Test
    void shouldMarkUnconfiguredTencentAsQualificationRequired() {
        ChannelAvailability result = service().evaluate(smsAccount("{\"scriptName\":\"TencentSmsScript\"}"));

        assertEquals(ChannelAvailabilityStatus.DISABLED, result.getStatus());
        assertFalse(result.isReady());
    }

    @Test
    void shouldMarkConfiguredTencentReadyAndKeepQualificationFlag() {
        String config = "{\"url\":\"sms.tencentcloudapi.com\",\"region\":\"ap-guangzhou\","
                + "\"secretId\":\"id\",\"secretKey\":\"key\",\"smsSdkAppId\":\"app\","
                + "\"templateId\":\"tpl\",\"signName\":\"sign\",\"scriptName\":\"TencentSmsScript\"}";

        MockEnvironment environment = new MockEnvironment()
                .withProperty("austin.channels.sms.enabled-scripts", "MockSmsScript,TencentSmsScript");
        environment.setActiveProfiles("dev");
        ChannelAvailability result = new ChannelAccountAvailabilityService(dao, environment, null, null, null)
                .evaluate(smsAccount(config));

        assertEquals(ChannelAvailabilityStatus.READY, result.getStatus());
        assertTrue(result.isQualificationRequired());
    }

    @Test
    void shouldClassifyMissingMiniProgramAsClientRequired() {
        when(dao.findAllByIsDeletedEqualsAndCreatorEqualsAndSendChannelEquals(
                CommonConstant.FALSE, "3y", ChannelType.MINI_PROGRAM.getCode())).thenReturn(Collections.emptyList());

        ChannelAvailability result = service().checkChannel(ChannelType.MINI_PROGRAM.getCode(), "3y");

        assertEquals(ChannelAvailabilityStatus.DISABLED, result.getStatus());
        assertFalse(result.isReady());
    }

    @Test
    void shouldRecognizeReadyEnvironmentFeiShuRobotAccount() {
        EnvironmentFeiShuRobotAccountProvider provider = mock(EnvironmentFeiShuRobotAccountProvider.class);
        when(provider.availability()).thenReturn(ChannelAvailability.builder()
                .status(ChannelAvailabilityStatus.READY).build());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        ChannelAccountAvailabilityService service = new ChannelAccountAvailabilityService(
                dao, environment, null, null, provider);

        ChannelAvailability result = service.check(ChannelType.FEI_SHU_ROBOT.getCode(),
                EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID, "3y");

        assertEquals(ChannelAvailabilityStatus.READY, result.getStatus());
    }

    @Test
    void shouldCheckAutoFlowAgainstConfiguredScriptAccount() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getProperty("msgTypeSmsConfig", "[]"))
                .thenReturn("[{\"message_type_10\":[{\"weights\":100,\"scriptName\":\"MockSmsScript\"}]}]");
        when(dao.findAllByIsDeletedEqualsAndSendChannelEquals(CommonConstant.FALSE, ChannelType.SMS.getCode()))
                .thenReturn(Collections.singletonList(smsAccount("{\"scriptName\":\"MockSmsScript\"}")));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        ChannelAvailability result = new ChannelAccountAvailabilityService(dao, environment, configService, null, null)
                .checkSmsFlow(10);

        assertEquals(ChannelAvailabilityStatus.READY, result.getStatus());
    }

    @Test
    void shouldRejectSmsFlowWhenScriptDoesNotMatchAccount() {
        ChannelAccount account = smsAccount("{\"scriptName\":\"MockSmsScript\"}");
        when(dao.findById(1L)).thenReturn(java.util.Optional.of(account));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("austin.channels.sms.enabled-scripts", "MockSmsScript,TencentSmsScript");

        ChannelAvailability result = new ChannelAccountAvailabilityService(dao, environment, null, null, null)
                .checkSmsScript("TencentSmsScript", 1);

        assertEquals(ChannelAvailabilityStatus.NOT_CONFIGURED, result.getStatus());
        assertTrue(result.getMessage().contains("不匹配"));
    }

    @Test
    void shouldScopeChannelLookupByTenant() {
        ChannelAccount account = smsAccount("{\"scriptName\":\"MockSmsScript\"}");
        account.setTenantId(7L);
        when(dao.findByIdAndTenantId(1L, 7L)).thenReturn(java.util.Optional.of(account));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        ChannelAvailability result = new ChannelAccountAvailabilityService(
                dao, environment, null, null, null).checkSmsScript("MockSmsScript", 1, 7L);

        assertEquals(ChannelAvailabilityStatus.READY, result.getStatus());
    }

    private ChannelAccountAvailabilityService service() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        return new ChannelAccountAvailabilityService(dao, environment, null, null, null);
    }

    private ChannelAccount smsAccount(String config) {
        return ChannelAccount.builder().id(1L).name("sms").sendChannel(ChannelType.SMS.getCode())
                .accountConfig(config).isDeleted(CommonConstant.FALSE).creator("3y").build();
    }
}
