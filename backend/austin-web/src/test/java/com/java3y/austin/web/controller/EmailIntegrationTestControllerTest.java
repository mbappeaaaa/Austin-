package com.java3y.austin.web.controller;

import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.handler.handler.impl.EmailHandler;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.service.EnvironmentEmailAccountProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailIntegrationTestControllerTest {

    @Test
    void shouldOnlySendToRecipientConfiguredByEnvironment() {
        EmailHandler handler = mock(EmailHandler.class);
        EnvironmentEmailAccountProvider provider = mock(EnvironmentEmailAccountProvider.class);
        when(provider.availability()).thenReturn(ChannelAvailability.builder()
                .status(ChannelAvailabilityStatus.READY).build());
        when(provider.getTestRecipient()).thenReturn("fixed-recipient@example.test");
        when(handler.handler(any(TaskInfo.class))).thenReturn(true);
        EmailIntegrationTestController controller = new EmailIntegrationTestController(handler, provider);

        controller.send();

        ArgumentCaptor<TaskInfo> captor = ArgumentCaptor.forClass(TaskInfo.class);
        verify(handler).handler(captor.capture());
        assertEquals(1, captor.getValue().getReceiver().size());
        assertTrue(captor.getValue().getReceiver().contains("fixed-recipient@example.test"));
        assertEquals(Integer.valueOf(EnvironmentEmailAccountProvider.ENVIRONMENT_EMAIL_ACCOUNT_ID),
                captor.getValue().getSendAccount());
    }

    @Test
    void shouldBeDisabledWhenEnablePropertyIsMissing() {
        ConditionalOnProperty condition = EmailIntegrationTestController.class
                .getAnnotation(ConditionalOnProperty.class);

        assertEquals("austin.email.test.enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }
}
