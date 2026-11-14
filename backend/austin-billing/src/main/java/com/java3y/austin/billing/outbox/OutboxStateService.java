package com.java3y.austin.billing.outbox;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.domain.OutboxStatus;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class OutboxStateService {
    private static final int MAX_PAGE_SIZE = 100;
    private final OutboxEventRepository repository;
    private final Clock clock;

    @Autowired
    public OutboxStateService(OutboxEventRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OutboxStateService(OutboxEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Optional<OutboxEvent> claim(Long id, String workerId, int leaseSeconds) {
        requireWorker(workerId);
        if (leaseSeconds <= 0 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("Outbox租约必须在1到3600秒之间");
        }
        OutboxEvent event = repository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outbox事件不存在"));
        int now = now();
        if (!ready(event, now)) {
            return Optional.empty();
        }
        event.setStatus(OutboxStatus.PROCESSING);
        event.setAttemptCount(defaultInt(event.getAttemptCount()) + 1);
        event.setLockedBy(workerId);
        event.setLockedUntil(now + leaseSeconds);
        event.setNextRetryAt(null);
        event.setUpdated(now);
        return Optional.of(event);
    }

    @Transactional
    public void published(Long id, String workerId) {
        OutboxEvent event = owned(id, workerId);
        int now = now();
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(now);
        event.setLockedBy(null);
        event.setLockedUntil(null);
        event.setLastError(null);
        event.setUpdated(now);
    }

    @Transactional
    public OutboxStatus failed(Long id, String workerId, Throwable failure) {
        OutboxEvent event = owned(id, workerId);
        int now = now();
        int attempts = defaultInt(event.getAttemptCount());
        int maxAttempts = event.getMaxAttempts() == null ? 5 : event.getMaxAttempts();
        OutboxStatus status = attempts >= maxAttempts ? OutboxStatus.DEAD : OutboxStatus.RETRY_WAIT;
        event.setStatus(status);
        event.setNextRetryAt(status == OutboxStatus.RETRY_WAIT ? now + retryDelaySeconds(attempts) : null);
        event.setLockedBy(null);
        event.setLockedUntil(null);
        event.setLastError(safeFailureCode(failure));
        event.setUpdated(now);
        return status;
    }

    @Transactional
    public OutboxEvent replayDead(Long tenantId, Long id) {
        requireTenant(tenantId);
        OutboxEvent event = repository.findLockedByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox事件不存在"));
        if (event.getStatus() != OutboxStatus.DEAD) {
            throw new OutboxOperationConflictException("只有死信Outbox事件可以人工重放");
        }
        int now = now();
        event.setStatus(OutboxStatus.PENDING);
        event.setAttemptCount(0);
        event.setReplayCount(defaultInt(event.getReplayCount()) + 1);
        event.setNextRetryAt(null);
        event.setLockedBy(null);
        event.setLockedUntil(null);
        event.setLastError(null);
        event.setUpdated(now);
        return event;
    }

    @Transactional(readOnly = true)
    public Page<OutboxEvent> listDead(Long tenantId, int page, int pageSize) {
        requireTenant(tenantId);
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("分页参数无效，每页最多100条");
        }
        return repository.findByTenantIdAndStatusOrderByUpdatedDescIdDesc(
                tenantId, OutboxStatus.DEAD, PageRequest.of(page - 1, pageSize));
    }

    private boolean ready(OutboxEvent event, int now) {
        if (event.getStatus() == OutboxStatus.PENDING) {
            return true;
        }
        if (event.getStatus() == OutboxStatus.RETRY_WAIT) {
            return event.getNextRetryAt() != null && event.getNextRetryAt() <= now;
        }
        return event.getStatus() == OutboxStatus.PROCESSING
                && event.getLockedUntil() != null && event.getLockedUntil() <= now;
    }

    private OutboxEvent owned(Long id, String workerId) {
        requireWorker(workerId);
        OutboxEvent event = repository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outbox事件不存在"));
        if (event.getStatus() != OutboxStatus.PROCESSING
                || !workerId.equals(event.getLockedBy())) {
            throw new OutboxOperationConflictException("当前执行器未持有Outbox事件租约");
        }
        return event;
    }

    private int retryDelaySeconds(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 6);
        return 5 * (1 << exponent);
    }

    private String safeFailureCode(Throwable failure) {
        String name = failure == null ? "UnknownFailure" : failure.getClass().getSimpleName();
        return name.length() <= 512 ? name : name.substring(0, 512);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int now() {
        return Math.toIntExact(Instant.now(clock).getEpochSecond());
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
    }

    private void requireWorker(String workerId) {
        if (workerId == null || workerId.trim().isEmpty() || workerId.length() > 128) {
            throw new IllegalArgumentException("Outbox执行器ID不能为空或过长");
        }
    }
}
