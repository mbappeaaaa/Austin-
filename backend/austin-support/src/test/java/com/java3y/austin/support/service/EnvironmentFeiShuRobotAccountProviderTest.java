package com.java3y.austin.support.service;

import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentFeiShuRobotAccountProviderTest {

    @Test
    void shouldLoadOfficialWebhookFromEnvironmentMapping() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("austin.feishu.robot.enabled", "true")
                .withProperty("austin.feishu.robot.webhook",
                        "https://open.feishu.cn/open-apis/bot/v2/hook/example-token");
        EnvironmentFeiShuRobotAccountProvider provider = new EnvironmentFeiShuRobotAccountProvider(environment);

        provider.afterPropertiesSet();

        assertEquals(ChannelAvailabilityStatus.READY, provider.availability().getStatus());
        assertEquals("https://open.feishu.cn/open-apis/bot/v2/hook/example-token",
                provider.getAccount().getWebhook());
    }

    @Test
    void shouldFailStartupWhenEnabledWebhookIsMissing() {
        EnvironmentFeiShuRobotAccountProvider provider = new EnvironmentFeiShuRobotAccountProvider(
                new MockEnvironment().withProperty("austin.feishu.robot.enabled", "true"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, provider::afterPropertiesSet);

        assertTrue(exception.getMessage().contains("AUSTIN_FEISHU_ROBOT_WEBHOOK"));
    }

    @Test
    void shouldRejectNonOfficialOrInsecureWebhook() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("austin.feishu.robot.enabled", "true")
                .withProperty("austin.feishu.robot.webhook", "http://example.test/open-apis/bot/v2/hook/token");
        EnvironmentFeiShuRobotAccountProvider provider = new EnvironmentFeiShuRobotAccountProvider(environment);

        assertThrows(IllegalStateException.class, provider::afterPropertiesSet);
        assertEquals(ChannelAvailabilityStatus.NOT_CONFIGURED, provider.availability().getStatus());
    }
}
