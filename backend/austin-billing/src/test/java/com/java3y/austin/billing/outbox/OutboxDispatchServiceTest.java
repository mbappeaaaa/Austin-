package com.java3y.austin.billing.outbox;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.domain.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatchServiceTest {
    @Test
    void shouldPublishAfterIdempotentHandlerSucceeds() {
        OutboxStateService state = mock(OutboxStateService.class);
        OutboxEventHandler handler = handler("payment.succeeded");
        OutboxEvent event = event();
        when(state.claim(11L, "worker", 60)).thenReturn(Optional.of(event));
        OutboxDispatchService service = new OutboxDispatchService(state,
                Arrays.asList(handler), "worker");

        assertTrue(service.dispatch(11L, 60));
        verify(handler).handle(any(OutboxMessage.class));
        verify(state).published(11L, "worker");
    }

    @Test
    void shouldMoveFailedHandlerThroughStateMachine() {
        OutboxStateService state = mock(OutboxStateService.class);
        OutboxEventHandler handler = handler("payment.succeeded");
        OutboxEvent event = event();
        when(state.claim(11L, "worker", 60)).thenReturn(Optional.of(event));
        doThrow(new IllegalStateException()).when(handler).handle(any(OutboxMessage.class));
        when(state.failed(any(), any(), any())).thenReturn(OutboxStatus.RETRY_WAIT);
        OutboxDispatchService service = new OutboxDispatchService(state,
                Arrays.asList(handler), "worker");

        assertFalse(service.dispatch(11L, 60));
        verify(state).failed(any(), any(), any(IllegalStateException.class));
    }

    private OutboxEventHandler handler(String type) {
        OutboxEventHandler handler = mock(OutboxEventHandler.class);
        when(handler.eventType()).thenReturn(type);
        return handler;
    }

    private OutboxEvent event() {
        return OutboxEvent.builder().id(11L).tenantId(7L).aggregateType("PAYMENT_ORDER")
                .aggregateId("pay_1").eventType("payment.succeeded")
                .eventKey("payment-succeeded:pay_1").payload("{}").build();
    }
}
