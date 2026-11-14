package com.java3y.austin.web.controller;

import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.dto.model.FeiShuRobotContentModel;
import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.handler.domain.feishu.FeiShuRobotSendResult;
import com.java3y.austin.handler.handler.impl.FeiShuRobotHandler;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.service.EnvironmentFeiShuRobotAccountProvider;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.service.FeiShuRobotTestAccessGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeiShuRobotIntegrationTestControllerTest {

    @Test
    void shouldReuseHandlerWithFixedNonSensitiveTextAndReturnResponseCode() {
        FeiShuRobotHandler handler = mock(FeiShuRobotHandler.class);
        EnvironmentFeiShuRobotAccountProvider provider = mock(EnvironmentFeiShuRobotAccountProvider.class);
        FeiShuRobotTestAccessGuard guard = mock(FeiShuRobotTestAccessGuard.class);
        when(guard.isAdministrator("admin-token")).thenReturn(true);
        when(provider.availability()).thenReturn(ChannelAvailability.builder()
                .status(ChannelAvailabilityStatus.READY).build());
        when(handler.sendWithResult(any(TaskInfo.class))).thenReturn(FeiShuRobotSendResult.builder()
                .success(false).responseCode(19024).errorReason("脱敏后的原因").build());
        FeiShuRobotIntegrationTestController controller =
                new FeiShuRobotIntegrationTestController(handler, provider, guard);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Austin-Admin-Token", "admin-token");

        Map<String, Object> result = controller.send(request);

        ArgumentCaptor<TaskInfo> captor = ArgumentCaptor.forClass(TaskInfo.class);
        verify(handler).sendWithResult(captor.capture());
        FeiShuRobotContentModel content = (FeiShuRobotContentModel) captor.getValue().getContentModel();
        assertEquals(FeiShuRobotIntegrationTestController.FIXED_TEST_TEXT, content.getContent());
        assertEquals(Integer.valueOf(EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID),
                captor.getValue().getSendAccount());
        assertEquals(19024, result.get("responseCode"));
        assertEquals("脱敏后的原因", result.get("errorReason"));
    }

    @Test
    void shouldRejectNonAdministrator() {
        FeiShuRobotTestAccessGuard guard = mock(FeiShuRobotTestAccessGuard.class);
        FeiShuRobotIntegrationTestController controller = new FeiShuRobotIntegrationTestController(
                mock(FeiShuRobotHandler.class), mock(EnvironmentFeiShuRobotAccountProvider.class), guard);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Austin-Admin-Token", "wrong-token");

        assertThrows(CommonException.class, () -> controller.send(request));
    }

    @Test
    void shouldBeDevelopmentOnlyAndDisabledByDefault() {
        Profile profile = FeiShuRobotIntegrationTestController.class.getAnnotation(Profile.class);
        ConditionalOnProperty condition = FeiShuRobotIntegrationTestController.class
                .getAnnotation(ConditionalOnProperty.class);

        assertEquals("dev", profile.value()[0]);
        assertEquals("local", profile.value()[1]);
        assertEquals("austin.feishu.robot.test.enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }
}
