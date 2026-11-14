package com.java3y.austin.handler.inbox;

import com.java3y.austin.handler.pending.Task;
import com.java3y.austin.handler.pending.TaskPendingHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboxDispatchServiceTest {

    private InboxService inboxService;
    private ExecutorService executor;
    private InboxDispatchService service;

    @BeforeEach
    void setUp() {
        inboxService = mock(InboxService.class);
        ApplicationContext context = mock(ApplicationContext.class);
        TaskPendingHolder holder = mock(TaskPendingHolder.class);
        executor = mock(ExecutorService.class);
        when(holder.route("email.notice")).thenReturn(executor);
        when(context.getBean(Task.class)).thenReturn(new Task());
        service = new InboxDispatchService(inboxService, context, holder);
        ReflectionTestUtils.setField(service, "leaseSeconds", 60L);
        ReflectionTestUtils.setField(service, "retryBaseSeconds", 5L);
        InboxMessage message = InboxMessage.builder().id(11L).tenantId(7L)
                .consumerGroup("email.notice").payload("payload").build();
        when(inboxService.claim(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(11L), anyString(), org.mockito.ArgumentMatchers.eq(60L)))
                .thenReturn(Optional.of(message));
    }

    @Test
    void shouldClaimBeforeSubmittingToExecutor() {
        assertTrue(service.dispatch(7L, 11L, "email.notice"));

        verify(executor).execute(org.mockito.ArgumentMatchers.any(Task.class));
    }

    @Test
    void shouldMoveRejectedSubmissionToRetryState() {
        doThrow(new RejectedExecutionException("full")).when(executor)
                .execute(org.mockito.ArgumentMatchers.any(Task.class));

        assertFalse(service.dispatch(7L, 11L, "email.notice"));

        verify(inboxService).fail(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(11L), anyString(),
                org.mockito.ArgumentMatchers.eq("EXECUTOR_REJECTED"),
                org.mockito.ArgumentMatchers.eq("message executor rejected task"),
                org.mockito.ArgumentMatchers.eq(5L));
    }
}
