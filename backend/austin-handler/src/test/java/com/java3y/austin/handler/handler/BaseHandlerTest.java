package com.java3y.austin.handler.handler;

import com.java3y.austin.common.domain.RecallTaskInfo;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.service.ChannelAccountAvailabilityService;
import com.java3y.austin.support.utils.LogUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaseHandlerTest {

    private static final Long TENANT_ID = 42L;

    @Test
    void shouldCheckAutoSmsAccountWithinTaskTenant() {
        ChannelAccountAvailabilityService availabilityService = readyAvailabilityService();
        TestHandler handler = handler(availabilityService);
        TaskInfo taskInfo = TaskInfo.builder()
                .tenantId(TENANT_ID)
                .sendChannel(ChannelType.SMS.getCode())
                .sendAccount(0)
                .msgType(10)
                .build();

        DeliveryResult result = handler.doHandler(taskInfo);

        verify(availabilityService).checkSmsFlow(10, TENANT_ID);
        assertTrue(handler.invoked);
        assertEquals(DeliveryStatus.SUCCESS, result.getStatus());
    }

    @Test
    void shouldCheckExplicitAccountWithinTaskTenant() {
        ChannelAccountAvailabilityService availabilityService = readyAvailabilityService();
        TestHandler handler = handler(availabilityService);
        TaskInfo taskInfo = TaskInfo.builder()
                .tenantId(TENANT_ID)
                .sendChannel(ChannelType.EMAIL.getCode())
                .sendAccount(7)
                .build();

        DeliveryResult result = handler.doHandler(taskInfo);

        verify(availabilityService).check(ChannelType.EMAIL.getCode(), 7, null, TENANT_ID);
        assertTrue(handler.invoked);
        assertEquals(DeliveryStatus.SUCCESS, result.getStatus());
    }

    @Test
    void shouldReturnFinalFailureWhenChannelIsUnavailable() {
        ChannelAccountAvailabilityService availabilityService = mock(ChannelAccountAvailabilityService.class);
        when(availabilityService.check(ChannelType.EMAIL.getCode(), 7, null, TENANT_ID))
                .thenReturn(ChannelAvailability.builder()
                        .status(ChannelAvailabilityStatus.DISABLED).build());
        TestHandler handler = handler(availabilityService);
        TaskInfo taskInfo = TaskInfo.builder().tenantId(TENANT_ID)
                .sendChannel(ChannelType.EMAIL.getCode()).sendAccount(7).build();

        DeliveryResult result = handler.doHandler(taskInfo);

        assertEquals(DeliveryStatus.FINAL_FAILURE, result.getStatus());
        assertEquals(false, handler.invoked);
    }

    private ChannelAccountAvailabilityService readyAvailabilityService() {
        ChannelAccountAvailabilityService service = mock(ChannelAccountAvailabilityService.class);
        ChannelAvailability ready = ChannelAvailability.builder()
                .status(ChannelAvailabilityStatus.READY)
                .build();
        when(service.checkSmsFlow(10, TENANT_ID)).thenReturn(ready);
        when(service.check(ChannelType.EMAIL.getCode(), 7, null, TENANT_ID)).thenReturn(ready);
        return service;
    }

    private TestHandler handler(ChannelAccountAvailabilityService availabilityService) {
        TestHandler handler = new TestHandler();
        ReflectionTestUtils.setField(handler, "channelAccountAvailabilityService", availabilityService);
        ReflectionTestUtils.setField(handler, "logUtils", mock(LogUtils.class));
        return handler;
    }

    private static class TestHandler extends BaseHandler {
        private boolean invoked;

        @Override
        public boolean handler(TaskInfo taskInfo) {
            invoked = true;
            return true;
        }

        @Override
        public void recall(RecallTaskInfo recallTaskInfo) {
            // This test handler only exercises the send path.
        }
    }
}
