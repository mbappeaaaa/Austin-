package com.java3y.austin.handler.pending;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.pipeline.ProcessController;
import com.java3y.austin.handler.inbox.InboxService;
import com.java3y.austin.handler.delivery.NonRetryableDeliveryException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskTest {

    @Test
    void shouldMarkInboxSucceededAfterPipelineCompletes() {
        InboxService inboxService = mock(InboxService.class);
        Task task = task(inboxService, mock(ProcessController.class));

        task.run();

        verify(inboxService).succeed(7L, 11L, "worker-a");
    }

    @Test
    void shouldMoveInboxToRetryWhenPipelineThrows() {
        InboxService inboxService = mock(InboxService.class);
        ProcessController controller = mock(ProcessController.class);
        doThrow(new IllegalStateException("provider unavailable")).when(controller).process(any());
        Task task = task(inboxService, controller);

        task.run();

        verify(inboxService).fail(7L, 11L, "worker-a", "IllegalStateException",
                "message processing failed", 5L);
    }

    @Test
    void shouldDeadLetterUnknownDeliveryWithoutRetry() {
        InboxService inboxService = mock(InboxService.class);
        ProcessController controller = mock(ProcessController.class);
        doThrow(new NonRetryableDeliveryException("DELIVERY_UNKNOWN", "manual review"))
                .when(controller).process(any());
        Task task = task(inboxService, controller);

        task.run();

        verify(inboxService).deadLetter(7L, 11L, "worker-a",
                "DELIVERY_UNKNOWN", "manual review");
    }

    private Task task(InboxService inboxService, ProcessController controller) {
        TaskInfo taskInfo = TaskInfo.builder().tenantId(7L).messageId("msg-1").build();
        Task task = new Task().setTenantId(7L).setInboxId(11L).setWorkerId("worker-a")
                .setPayload(JSON.toJSONString(taskInfo)).setRetryBaseSeconds(5L);
        ReflectionTestUtils.setField(task, "inboxService", inboxService);
        ReflectionTestUtils.setField(task, "processController", controller);
        return task;
    }
}
