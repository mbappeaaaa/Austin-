package com.java3y.austin.billing.payment.service;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.domain.PaymentEvent;
import com.java3y.austin.billing.payment.domain.PaymentEventStatus;
import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;
import com.java3y.austin.billing.payment.provider.VerifiedPaymentEvent;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import com.java3y.austin.billing.payment.repository.PaymentEventRepository;
import com.java3y.austin.billing.payment.repository.PaymentOrderRepository;
import com.java3y.austin.billing.service.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentWebhookProcessorTest {
    private PaymentEventRepository eventRepository;
    private PaymentOrderRepository orderRepository;
    private OutboxEventRepository outboxRepository;
    private QuotaService quotaService;
    private PaymentWebhookProcessor processor;

    @BeforeEach
    void setUp() {
        eventRepository = mock(PaymentEventRepository.class);
        orderRepository = mock(PaymentOrderRepository.class);
        outboxRepository = mock(OutboxEventRepository.class);
        quotaService = mock(QuotaService.class);
        processor = new PaymentWebhookProcessor(eventRepository, orderRepository,
                outboxRepository, quotaService);
    }

    @Test
    void shouldAtomicallyCreditQuotaAndAppendOutboxOnFirstSuccess() {
        VerifiedPaymentEvent verified = successEvent();
        PaymentEvent storedEvent = PaymentEvent.builder().provider(PaymentProviderType.STRIPE)
                .providerEventId("evt-1").payloadHash("hash")
                .eventStatus(PaymentEventStatus.RECEIVED).build();
        PaymentOrder order = PaymentOrder.builder().tenantId(7L).orderNo("pay-1")
                .provider(PaymentProviderType.STRIPE).providerOrderId("session-1")
                .amountMinor(100L).currency("USD").quotaAmount(1000L)
                .status(PaymentOrderStatus.PENDING).build();
        when(eventRepository.insertIgnore(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(eventRepository.findByProviderAndProviderEventId(PaymentProviderType.STRIPE, "evt-1"))
                .thenReturn(Optional.of(storedEvent));
        when(orderRepository.findLockedByOrderNo("pay-1")).thenReturn(Optional.of(order));
        when(outboxRepository.findByEventKey("payment-succeeded:pay-1"))
                .thenReturn(Optional.empty());

        PaymentWebhookResult result = processor.process(verified);

        assertEquals(PaymentOrderStatus.SUCCEEDED, order.getStatus());
        assertEquals(PaymentEventStatus.PROCESSED, storedEvent.getEventStatus());
        assertFalse(result.isDuplicate());
        verify(quotaService).credit(7L, 1000L, "payment-credit:pay-1", "PAYMENT", "pay-1");
        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals(PaymentWebhookProcessor.PAYMENT_SUCCEEDED_EVENT,
                outbox.getValue().getEventType());
    }

    @Test
    void shouldIgnoreDuplicateEventWithoutCreditingAgain() {
        VerifiedPaymentEvent verified = successEvent();
        PaymentEvent storedEvent = PaymentEvent.builder().provider(PaymentProviderType.STRIPE)
                .providerEventId("evt-1").orderNo("pay-1").payloadHash("hash")
                .eventStatus(PaymentEventStatus.PROCESSED).build();
        when(eventRepository.insertIgnore(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(eventRepository.findByProviderAndProviderEventId(PaymentProviderType.STRIPE, "evt-1"))
                .thenReturn(Optional.of(storedEvent));

        PaymentWebhookResult result = processor.process(verified);

        assertEquals(true, result.isDuplicate());
        verify(quotaService, never()).credit(any(), anyLong(), any(), any(), any());
        verify(outboxRepository, never()).save(any());
    }

    private VerifiedPaymentEvent successEvent() {
        return VerifiedPaymentEvent.builder().provider(PaymentProviderType.STRIPE)
                .providerEventId("evt-1").providerOrderId("session-1").orderNo("pay-1")
                .eventType("checkout.session.completed").status(VerifiedPaymentStatus.SUCCEEDED)
                .amountMinor(100L).currency("USD").providerCreatedAt(1_800_000_000)
                .liveMode(false).payloadHash("hash").build();
    }
}
