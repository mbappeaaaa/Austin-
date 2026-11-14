package com.java3y.austin.handler.action;

import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.handler.delivery.QuotaDeliveryService;
import com.java3y.austin.handler.handler.DeliveryResult;
import com.java3y.austin.handler.handler.Handler;
import com.java3y.austin.handler.handler.HandlerHolder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendMessageActionTest {

    @Test
    void shouldSettleEachRecipientAndSkipPreviouslyCompletedRecipient() {
        HandlerHolder holder = mock(HandlerHolder.class);
        Handler handler = mock(Handler.class);
        QuotaDeliveryService quotaDeliveryService = mock(QuotaDeliveryService.class);
        SendMessageAction action = new SendMessageAction();
        ReflectionTestUtils.setField(action, "handlerHolder", holder);
        ReflectionTestUtils.setField(action, "quotaDeliveryService", quotaDeliveryService);
        TaskInfo taskInfo = TaskInfo.builder().tenantId(7L).quotaReservationId(21L)
                .messageId("msg-1").sendChannel(ChannelType.MINI_PROGRAM.getCode())
                .receiver(new LinkedHashSet<>(Arrays.asList("receiver-a", "receiver-b"))).build();
        when(holder.route(ChannelType.MINI_PROGRAM.getCode())).thenReturn(handler);
        when(quotaDeliveryService.receiverOperationKey(taskInfo, "receiver-a")).thenReturn("op-a");
        when(quotaDeliveryService.receiverOperationKey(taskInfo, "receiver-b")).thenReturn("op-b");
        when(quotaDeliveryService.isAlreadyCompleted(taskInfo, "op-a")).thenReturn(true);
        DeliveryResult success = DeliveryResult.success();
        when(handler.doHandler(org.mockito.ArgumentMatchers.any(TaskInfo.class)))
                .thenReturn(success);

        action.process(ProcessContext.<TaskInfo>builder().processModel(taskInfo).build());

        verify(handler).doHandler(org.mockito.ArgumentMatchers.argThat(
                task -> task.getReceiver().contains("receiver-b") && task.getReceiver().size() == 1));
        verify(quotaDeliveryService).complete(taskInfo, 1L, "op-b", success);
        verify(quotaDeliveryService).releaseRemaining(taskInfo, "PIPELINE_FILTERED");
    }
}
