package com.java3y.austin.handler.inbox;

import cn.hutool.crypto.digest.DigestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InboxServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final long NOW = 1_800_000_000L;

    private InboxMessageRepository repository;
    private InboxService service;

    @BeforeEach
    void setUp() {
        repository = mock(InboxMessageRepository.class);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
        service = new InboxService(repository, clock);
    }

    @Test
    void shouldRegisterOnceAndReturnExistingMessageForSamePayload() {
        String payload = "{\"messageId\":\"msg-1\"}";
        InboxMessage existing = message(InboxStatus.PENDING);
        existing.setPayloadHash(DigestUtil.sha256Hex(payload));
        when(repository.insertIgnore(TENANT_ID, "send", "msg-1", existing.getPayloadHash(),
                payload, 3, Math.toIntExact(NOW))).thenReturn(1, 0);
        when(repository.findByTenantIdAndConsumerGroupAndConsumeKey(TENANT_ID, "send", "msg-1"))
                .thenReturn(Optional.of(existing));

        assertTrue(service.register(TENANT_ID, "send", "msg-1", payload, 3).isCreated());
        assertFalse(service.register(TENANT_ID, "send", "msg-1", payload, 3).isCreated());
    }

    @Test
    void shouldRejectSameConsumeKeyWithDifferentPayload() {
        InboxMessage existing = message(InboxStatus.PENDING);
        existing.setPayloadHash(DigestUtil.sha256Hex("old-payload"));
        when(repository.insertIgnore(TENANT_ID, "send", "msg-1",
                DigestUtil.sha256Hex("new-payload"), "new-payload", 3, Math.toIntExact(NOW)))
                .thenReturn(0);
        when(repository.findByTenantIdAndConsumerGroupAndConsumeKey(TENANT_ID, "send", "msg-1"))
                .thenReturn(Optional.of(existing));

        assertThrows(InboxOperationConflictException.class,
                () -> service.register(TENANT_ID, "send", "msg-1", "new-payload", 3));
    }

    @Test
    void shouldClaimWithLeaseAndCompleteOnlyByLeaseOwner() {
        InboxMessage message = message(InboxStatus.PENDING);
        when(repository.findLockedByIdAndTenantId(11L, TENANT_ID)).thenReturn(Optional.of(message));

        InboxMessage claimed = service.claim(TENANT_ID, 11L, "worker-a", 30).orElseThrow(AssertionError::new);

        assertEquals(InboxStatus.PROCESSING, claimed.getStatus());
        assertEquals(1, claimed.getAttemptCount());
        assertEquals(NOW + 30, claimed.getLockedUntil());
        assertFalse(service.claim(TENANT_ID, 11L, "worker-b", 30).isPresent());
        assertThrows(InboxOperationConflictException.class,
                () -> service.succeed(TENANT_ID, 11L, "worker-b"));

        service.succeed(TENANT_ID, 11L, "worker-a");
        assertEquals(InboxStatus.SUCCEEDED, message.getStatus());
        assertNull(message.getLockOwner());
    }

    @Test
    void shouldRetryWithBackoffThenBecomeDeadAndAllowManualReplay() {
        InboxMessage message = message(InboxStatus.PROCESSING);
        message.setAttemptCount(1);
        message.setLockOwner("worker-a");
        when(repository.findLockedByIdAndTenantId(11L, TENANT_ID)).thenReturn(Optional.of(message));

        service.fail(TENANT_ID, 11L, "worker-a", "TEMPORARY", "temporary failure", 10);
        assertEquals(InboxStatus.RETRY_WAIT, message.getStatus());
        assertEquals(NOW + 10, message.getNextRetryAt());

        message.setStatus(InboxStatus.PROCESSING);
        message.setAttemptCount(3);
        message.setLockOwner("worker-b");
        service.fail(TENANT_ID, 11L, "worker-b", "EXHAUSTED", "final failure", 10);
        assertEquals(InboxStatus.DEAD, message.getStatus());
        assertNull(message.getNextRetryAt());

        service.replayDead(TENANT_ID, 11L);
        assertEquals(InboxStatus.PENDING, message.getStatus());
        assertEquals(0, message.getAttemptCount());
        assertEquals(1, message.getReplayCount());
        assertNull(message.getLastErrorCode());
    }

    @Test
    void shouldDeadLetterImmediatelyForNonRetryableResult() {
        InboxMessage message = message(InboxStatus.PROCESSING);
        message.setLockOwner("worker-a");
        when(repository.findLockedByIdAndTenantId(11L, TENANT_ID)).thenReturn(Optional.of(message));

        service.deadLetter(TENANT_ID, 11L, "worker-a", "DELIVERY_UNKNOWN", "manual review");

        assertEquals(InboxStatus.DEAD, message.getStatus());
        assertEquals("DELIVERY_UNKNOWN", message.getLastErrorCode());
        assertEquals("manual review", message.getLastErrorMessage());
        assertNull(message.getLockOwner());
        assertNull(message.getNextRetryAt());
    }

    private InboxMessage message(InboxStatus status) {
        return InboxMessage.builder().id(11L).tenantId(TENANT_ID).consumerGroup("send")
                .consumeKey("msg-1").payloadHash("hash").payload("payload").status(status)
                .attemptCount(0).maxAttempts(3).replayCount(0)
                .created(Math.toIntExact(NOW)).updated(Math.toIntExact(NOW)).build();
    }
}
