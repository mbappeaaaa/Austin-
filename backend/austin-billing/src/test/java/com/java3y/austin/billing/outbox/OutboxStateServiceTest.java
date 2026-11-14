package com.java3y.austin.billing.outbox;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.domain.OutboxStatus;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxStateServiceTest {
    private OutboxEventRepository repository;
    private OutboxStateService service;
    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        service = new OutboxStateService(repository,
                Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC));
        event = OutboxEvent.builder().id(11L).tenantId(7L).eventType("event")
                .status(OutboxStatus.PENDING).attemptCount(0).maxAttempts(2)
                .replayCount(0).created(900).updated(900).build();
        when(repository.findLockedById(11L)).thenReturn(Optional.of(event));
        when(repository.findLockedByIdAndTenantId(11L, 7L)).thenReturn(Optional.of(event));
    }

    @Test
    void shouldClaimRetryThenDeadLetterAndReplay() {
        assertTrue(service.claim(11L, "worker", 30).isPresent());
        assertEquals(OutboxStatus.PROCESSING, event.getStatus());
        assertEquals(Integer.valueOf(1030), event.getLockedUntil());

        assertEquals(OutboxStatus.RETRY_WAIT,
                service.failed(11L, "worker", new IllegalStateException("sensitive detail")));
        assertEquals("IllegalStateException", event.getLastError());
        assertEquals(Integer.valueOf(1005), event.getNextRetryAt());

        assertFalse(service.claim(11L, "worker", 30).isPresent());
        event.setNextRetryAt(1000);
        assertTrue(service.claim(11L, "worker", 30).isPresent());
        assertEquals(OutboxStatus.DEAD,
                service.failed(11L, "worker", new IllegalArgumentException("payload")));

        service.replayDead(7L, 11L);
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(Integer.valueOf(0), event.getAttemptCount());
        assertEquals(Integer.valueOf(1), event.getReplayCount());
        assertNull(event.getLastError());
    }

    @Test
    void shouldPublishOnlyForLeaseOwner() {
        service.claim(11L, "worker", 30);
        service.published(11L, "worker");

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(Integer.valueOf(1000), event.getPublishedAt());
        assertNull(event.getLockedBy());
    }
}
