package com.java3y.austin.handler.inbox;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class InboxService {

    private static final int MAX_ATTEMPTS_LIMIT = 100;
    private static final long MAX_RETRY_DELAY_SECONDS = 3600L;

    private final InboxMessageRepository repository;
    private final Clock clock;

    @Autowired
    public InboxService(InboxMessageRepository repository) {
        this(repository, Clock.systemUTC());
    }

    InboxService(InboxMessageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public InboxRegistrationResult register(Long tenantId, String consumerGroup, String consumeKey,
                                            String payload, int maxAttempts) {
        requireTenant(tenantId);
        requireText(consumerGroup, 64, "消费组不能为空或过长");
        requireText(consumeKey, 128, "消费幂等键不能为空或过长");
        requireText(payload, Integer.MAX_VALUE, "消费消息内容不能为空");
        if (maxAttempts <= 0 || maxAttempts > MAX_ATTEMPTS_LIMIT) {
            throw new IllegalArgumentException("最大消费次数必须在1到100之间");
        }
        String payloadHash = DigestUtil.sha256Hex(payload);
        int inserted = repository.insertIgnore(tenantId, consumerGroup, consumeKey, payloadHash,
                payload, maxAttempts, nowSeconds());
        InboxMessage message = repository.findByTenantIdAndConsumerGroupAndConsumeKey(
                        tenantId, consumerGroup, consumeKey)
                .orElseThrow(() -> new IllegalStateException("Inbox消息登记后无法读取"));
        if (!Objects.equals(message.getPayloadHash(), payloadHash)
                || !Objects.equals(message.getMaxAttempts(), maxAttempts)) {
            throw new InboxOperationConflictException("相同消费幂等键对应不同消息内容或重试策略");
        }
        return new InboxRegistrationResult(message, inserted == 1);
    }

    @Transactional
    public Optional<InboxMessage> claim(Long tenantId, Long inboxId, String workerId,
                                        long leaseSeconds) {
        requireText(workerId, 64, "消费执行器标识不能为空或过长");
        if (leaseSeconds <= 0 || leaseSeconds > MAX_RETRY_DELAY_SECONDS) {
            throw new IllegalArgumentException("消费租约必须在1到3600秒之间");
        }
        InboxMessage message = lock(tenantId, inboxId);
        long now = nowSeconds();
        if (message.getStatus() == InboxStatus.SUCCEEDED || message.getStatus() == InboxStatus.DEAD) {
            return Optional.empty();
        }
        if (message.getStatus() == InboxStatus.PROCESSING
                && valueOrZero(message.getLockedUntil()) > now) {
            return Optional.empty();
        }
        if (message.getStatus() == InboxStatus.RETRY_WAIT
                && valueOrZero(message.getNextRetryAt()) > now) {
            return Optional.empty();
        }
        message.setStatus(InboxStatus.PROCESSING);
        message.setAttemptCount(valueOrZero(message.getAttemptCount()) + 1);
        message.setLockOwner(workerId);
        message.setLockedUntil(now + leaseSeconds);
        message.setNextRetryAt(null);
        message.setUpdated(Math.toIntExact(now));
        return Optional.of(message);
    }

    @Transactional
    public InboxMessage succeed(Long tenantId, Long inboxId, String workerId) {
        InboxMessage message = lock(tenantId, inboxId);
        if (message.getStatus() == InboxStatus.SUCCEEDED) {
            return message;
        }
        requireOwner(message, workerId);
        message.setStatus(InboxStatus.SUCCEEDED);
        clearLease(message);
        clearError(message);
        message.setUpdated(nowSeconds());
        return message;
    }

    @Transactional
    public InboxMessage fail(Long tenantId, Long inboxId, String workerId,
                             String errorCode, String errorMessage, long baseRetryDelaySeconds) {
        if (baseRetryDelaySeconds <= 0 || baseRetryDelaySeconds > MAX_RETRY_DELAY_SECONDS) {
            throw new IllegalArgumentException("重试基础间隔必须在1到3600秒之间");
        }
        InboxMessage message = lock(tenantId, inboxId);
        requireOwner(message, workerId);
        long now = nowSeconds();
        message.setLastErrorCode(truncate(errorCode, 64));
        message.setLastErrorMessage(truncate(errorMessage, 512));
        clearLease(message);
        if (valueOrZero(message.getAttemptCount()) >= message.getMaxAttempts()) {
            message.setStatus(InboxStatus.DEAD);
            message.setNextRetryAt(null);
        } else {
            int attempt = Math.max(1, valueOrZero(message.getAttemptCount()));
            long multiplier = 1L << Math.min(attempt - 1, 20);
            long delay = Math.min(MAX_RETRY_DELAY_SECONDS, baseRetryDelaySeconds * multiplier);
            message.setStatus(InboxStatus.RETRY_WAIT);
            message.setNextRetryAt(now + delay);
        }
        message.setUpdated(Math.toIntExact(now));
        return message;
    }

    @Transactional
    public InboxMessage deadLetter(Long tenantId, Long inboxId, String workerId,
                                   String errorCode, String errorMessage) {
        InboxMessage message = lock(tenantId, inboxId);
        requireOwner(message, workerId);
        message.setStatus(InboxStatus.DEAD);
        message.setLastErrorCode(truncate(errorCode, 64));
        message.setLastErrorMessage(truncate(errorMessage, 512));
        message.setNextRetryAt(null);
        clearLease(message);
        message.setUpdated(nowSeconds());
        return message;
    }

    @Transactional
    public InboxMessage replayDead(Long tenantId, Long inboxId) {
        InboxMessage message = lock(tenantId, inboxId);
        if (message.getStatus() != InboxStatus.DEAD) {
            throw new InboxOperationConflictException("只有死信消息可以人工重放");
        }
        message.setStatus(InboxStatus.PENDING);
        message.setAttemptCount(0);
        message.setReplayCount(valueOrZero(message.getReplayCount()) + 1);
        message.setNextRetryAt(null);
        clearLease(message);
        clearError(message);
        message.setUpdated(nowSeconds());
        return message;
    }

    private InboxMessage lock(Long tenantId, Long inboxId) {
        requireTenant(tenantId);
        if (inboxId == null || inboxId <= 0) {
            throw new IllegalArgumentException("Inbox消息ID不能为空");
        }
        return repository.findLockedByIdAndTenantId(inboxId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Inbox消息不存在"));
    }

    private void requireOwner(InboxMessage message, String workerId) {
        requireText(workerId, 64, "消费执行器标识不能为空或过长");
        if (message.getStatus() != InboxStatus.PROCESSING
                || !Objects.equals(message.getLockOwner(), workerId)) {
            throw new InboxOperationConflictException("当前执行器未持有Inbox消费租约");
        }
    }

    private void clearLease(InboxMessage message) {
        message.setLockOwner(null);
        message.setLockedUntil(null);
    }

    private void clearError(InboxMessage message) {
        message.setLastErrorCode(null);
        message.setLastErrorMessage(null);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
    }

    private void requireText(String value, int maxLength, String message) {
        if (value == null || value.trim().isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private int nowSeconds() {
        return Math.toIntExact(Instant.now(clock).getEpochSecond());
    }
}
