package com.java3y.austin.web.service;

import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.domain.MessageTemplate;
import com.java3y.austin.support.service.ChannelAccountAvailabilityService;
import com.java3y.austin.support.service.EnvironmentEmailAccountProvider;
import com.java3y.austin.support.service.EnvironmentFeiShuRobotAccountProvider;
import com.java3y.austin.web.exception.CommonException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageTemplateChannelValidatorTest {

    private static final Long TENANT_ID = 7L;

    private final ChannelAccountAvailabilityService availabilityService = mock(ChannelAccountAvailabilityService.class);
    private final MessageTemplateChannelValidator validator = new MessageTemplateChannelValidator(availabilityService);

    @Test
    void shouldRejectTemplateWhenAccountIsNotConfigured() {
        MessageTemplate template = MessageTemplate.builder().sendChannel(ChannelType.EMAIL.getCode())
                .sendAccount(99).creator("3y").tenantId(TENANT_ID).build();
        when(availabilityService.check(ChannelType.EMAIL.getCode(), 99, "3y", TENANT_ID))
                .thenReturn(ChannelAvailability.builder().status(ChannelAvailabilityStatus.NOT_CONFIGURED)
                        .message("尚未配置可用的渠道账号").build());

        CommonException exception = assertThrows(CommonException.class, () -> validator.validate(template));

        assertTrue(exception.getMessage().contains("NOT_CONFIGURED"));
        assertTrue(exception.getMessage().contains("尚未配置"));
    }

    @Test
    void shouldAllowReadyAccount() {
        MessageTemplate template = MessageTemplate.builder().sendChannel(ChannelType.EMAIL.getCode())
                .sendAccount(1).creator("3y").tenantId(TENANT_ID).build();
        when(availabilityService.check(ChannelType.EMAIL.getCode(), 1, "3y", TENANT_ID))
                .thenReturn(ChannelAvailability.builder().status(ChannelAvailabilityStatus.READY).build());

        assertDoesNotThrow(() -> validator.validate(template));
    }

    @Test
    void shouldRequireExplicitAccountForNonSmsChannel() {
        MessageTemplate template = MessageTemplate.builder().sendChannel(ChannelType.EMAIL.getCode())
                .sendAccount(0).creator("3y").tenantId(TENANT_ID).build();

        CommonException exception = assertThrows(CommonException.class, () -> validator.validate(template));

        assertTrue(exception.getMessage().contains("请先选择已配置的渠道账号"));
    }

    @Test
    void shouldAllowReadyEnvironmentEmailAccount() {
        MessageTemplate template = MessageTemplate.builder().sendChannel(ChannelType.EMAIL.getCode())
                .sendAccount(EnvironmentEmailAccountProvider.ENVIRONMENT_EMAIL_ACCOUNT_ID).creator("3y")
                .tenantId(TENANT_ID).build();
        when(availabilityService.check(ChannelType.EMAIL.getCode(),
                EnvironmentEmailAccountProvider.ENVIRONMENT_EMAIL_ACCOUNT_ID, "3y", TENANT_ID))
                .thenReturn(ChannelAvailability.builder().status(ChannelAvailabilityStatus.READY).build());

        assertDoesNotThrow(() -> validator.validate(template));
    }

    @Test
    void shouldAllowReadyEnvironmentFeiShuRobotAccount() {
        MessageTemplate template = MessageTemplate.builder().sendChannel(ChannelType.FEI_SHU_ROBOT.getCode())
                .sendAccount(EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID)
                .creator("3y").tenantId(TENANT_ID).build();
        when(availabilityService.check(ChannelType.FEI_SHU_ROBOT.getCode(),
                EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID, "3y", TENANT_ID))
                .thenReturn(ChannelAvailability.builder().status(ChannelAvailabilityStatus.READY).build());

        assertDoesNotThrow(() -> validator.validate(template));
    }

    @Test
    void shouldAllowImWithoutAccount() {
        MessageTemplate template = MessageTemplate.builder().sendChannel(ChannelType.IM.getCode())
                .sendAccount(0).creator("3y").tenantId(TENANT_ID).build();
        when(availabilityService.check(ChannelType.IM.getCode(), 0, "3y", TENANT_ID))
                .thenReturn(ChannelAvailability.builder().status(ChannelAvailabilityStatus.READY).build());

        assertDoesNotThrow(() -> validator.validate(template));
    }

    @Test
    void shouldRejectInvalidWebhookJsonTemplate() {
        MessageTemplate template = MessageTemplate.builder().sendChannel(ChannelType.WEBHOOK.getCode())
                .sendAccount(1).creator("3y").tenantId(TENANT_ID)
                .msgContent("{\"body\":\"not-json\"}").build();
        when(availabilityService.check(ChannelType.WEBHOOK.getCode(), 1, "3y", TENANT_ID))
                .thenReturn(ChannelAvailability.builder().status(ChannelAvailabilityStatus.READY).build());

        assertThrows(CommonException.class, () -> validator.validate(template));
    }
}
