package com.java3y.austin.billing.service;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaAlertOutboxAppenderTest {
    @Test
    void shouldAppendCycleScopedInsufficientEvent() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.findByEventKey("quota-insufficient:7:3")).thenReturn(Optional.empty());
        QuotaAlertOutboxAppender appender = new QuotaAlertOutboxAppender(repository);
        QuotaAccount account = QuotaAccount.builder().id(11L).tenantId(7L)
                .availableQuota(2L).reservedQuota(4L).warningThreshold(10L)
                .alertCycle(3L).build();

        appender.appendInsufficient(account, 5L, "MESSAGE", "1001:0");

        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(event.capture());
        assertEquals("quota-insufficient:7:3", event.getValue().getEventKey());
        assertEquals(5L, JSON.parseObject(event.getValue().getPayload()).getLongValue("requestedAmount"));
        assertEquals(QuotaAlertOutboxAppender.INSUFFICIENT_EVENT, event.getValue().getEventType());
    }
}
