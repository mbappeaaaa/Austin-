package com.java3y.austin.web.service;

import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.service.EnvironmentFeiShuRobotAccountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeiShuRobotTestAccessGuardTest {

    private static final String STRONG_TOKEN = "development-admin-token-123456";

    @Test
    void shouldAuthorizeMatchingDevelopmentAdministratorToken() throws Exception {
        MockEnvironment environment = enabledEnvironment("dev", STRONG_TOKEN);
        FeiShuRobotTestAccessGuard guard = guard(environment);

        guard.afterPropertiesSet();

        assertTrue(guard.isAdministrator(STRONG_TOKEN));
        assertFalse(guard.isAdministrator("wrong-token"));
    }

    @Test
    void shouldRejectTestEndpointInProductionAtStartup() {
        FeiShuRobotTestAccessGuard guard = guard(enabledEnvironment("prod", STRONG_TOKEN));

        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }

    @Test
    void shouldRejectWeakAdministratorTokenAtStartup() {
        FeiShuRobotTestAccessGuard guard = guard(enabledEnvironment("dev", "too-short"));

        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }

    @Test
    void shouldRejectEnabledTestWhenRobotAccountIsUnavailable() {
        EnvironmentFeiShuRobotAccountProvider accountProvider = mock(EnvironmentFeiShuRobotAccountProvider.class);
        when(accountProvider.availability()).thenReturn(ChannelAvailability.builder()
                .status(ChannelAvailabilityStatus.NOT_CONFIGURED).message("缺少 webhook").build());
        FeiShuRobotTestAccessGuard guard = new FeiShuRobotTestAccessGuard(
                enabledEnvironment("dev", STRONG_TOKEN), accountProvider);

        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }

    private MockEnvironment enabledEnvironment(String profile, String token) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("austin.feishu.robot.test.enabled", "true")
                .withProperty("austin.feishu.robot.test.admin-token", token);
        environment.setActiveProfiles(profile);
        return environment;
    }

    private FeiShuRobotTestAccessGuard guard(MockEnvironment environment) {
        EnvironmentFeiShuRobotAccountProvider accountProvider = mock(EnvironmentFeiShuRobotAccountProvider.class);
        when(accountProvider.availability()).thenReturn(ChannelAvailability.builder()
                .status(ChannelAvailabilityStatus.READY).build());
        return new FeiShuRobotTestAccessGuard(environment, accountProvider);
    }
}
