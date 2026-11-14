package com.java3y.austin.report.service;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.domain.OutboxStatus;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.domain.ReportStatus;
import com.java3y.austin.report.repository.ReportGenerationJobRepository;
import com.java3y.austin.report.storage.StoredReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportJobStateService {
    public static final String REPORT_COMPLETED_EVENT = "report.monthly.completed.v1";
    private final ReportGenerationJobRepository repository;
    private final OutboxEventRepository outboxRepository;
    private final Clock clock;

    @Autowired
    public ReportJobStateService(ReportGenerationJobRepository repository,
                                 OutboxEventRepository outboxRepository) {
        this(repository, outboxRepository, Clock.systemUTC());
    }

    ReportJobStateService(ReportGenerationJobRepository repository,
                          OutboxEventRepository outboxRepository, Clock clock) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<ReportGenerationJob> claim(Long id, String workerId, int leaseSeconds) {
        requireWorker(workerId);
        if (leaseSeconds <= 0 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("报告任务租约必须在1到3600秒之间");
        }
        ReportGenerationJob job = lock(id);
        int now = now();
        if (!ready(job, now)) {
            return Optional.empty();
        }
        job.setStatus(ReportStatus.PROCESSING);
        job.setAttemptCount(defaultInt(job.getAttemptCount()) + 1);
        job.setLockedBy(workerId);
        job.setLockedUntil(now + leaseSeconds);
        job.setNextRetryAt(null);
        job.setStartedAt(now);
        job.setUpdated(now);
        return Optional.of(job);
    }

    @Transactional
    public void complete(Long id, String workerId, StoredReport stored, String filename,
                         String contentType) {
        ReportGenerationJob job = owned(id, workerId);
        int now = now();
        job.setStatus(ReportStatus.COMPLETED);
        job.setStorageProvider(stored.getProvider());
        job.setStorageKey(stored.getStorageKey());
        job.setOriginalFilename(filename);
        job.setContentType(contentType);
        job.setFileSize(stored.getFileSize());
        job.setFileSha256(stored.getSha256());
        job.setCompletedAt(now);
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setLastError(null);
        job.setUpdated(now);
        appendCompletedOutbox(job, now);
    }

    @Transactional
    public ReportStatus failed(Long id, String workerId, Throwable failure) {
        ReportGenerationJob job = owned(id, workerId);
        int now = now();
        int attempts = defaultInt(job.getAttemptCount());
        int maxAttempts = job.getMaxAttempts() == null ? 3 : job.getMaxAttempts();
        ReportStatus status = attempts >= maxAttempts ? ReportStatus.DEAD : ReportStatus.RETRY_WAIT;
        job.setStatus(status);
        job.setNextRetryAt(status == ReportStatus.RETRY_WAIT
                ? now + retryDelaySeconds(attempts) : null);
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setLastError(safeFailureCode(failure));
        job.setUpdated(now);
        return status;
    }

    @Transactional
    public ReportGenerationJob replayDead(Long tenantId, Long id) {
        ReportGenerationJob job = repository.findLockedByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
        if (job.getStatus() != ReportStatus.DEAD) {
            throw new ReportOperationConflictException("只有失败终止的报告可以人工重试");
        }
        int now = now();
        job.setStatus(ReportStatus.PENDING);
        job.setAttemptCount(0);
        job.setReplayCount(defaultInt(job.getReplayCount()) + 1);
        job.setNextRetryAt(null);
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setLastError(null);
        job.setUpdated(now);
        return job;
    }

    private void appendCompletedOutbox(ReportGenerationJob job, int now) {
        String eventKey = "report-completed:" + job.getId();
        if (outboxRepository.findByEventKey(eventKey).isPresent()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", job.getTenantId());
        payload.put("reportId", job.getId());
        payload.put("reportType", job.getReportType().name());
        payload.put("periodStart", job.getPeriodStart().toString());
        payload.put("periodEnd", job.getPeriodEnd().toString());
        payload.put("filename", job.getOriginalFilename());
        payload.put("downloadPath", "/billing/reports/" + job.getId() + "/download");
        outboxRepository.save(OutboxEvent.builder().tenantId(job.getTenantId())
                .aggregateType("REPORT").aggregateId(String.valueOf(job.getId()))
                .eventType(REPORT_COMPLETED_EVENT).eventKey(eventKey)
                .payload(JSON.toJSONString(payload)).status(OutboxStatus.PENDING)
                .attemptCount(0).maxAttempts(5).replayCount(0)
                .created(now).updated(now).build());
    }

    private boolean ready(ReportGenerationJob job, int now) {
        if (job.getStatus() == ReportStatus.PENDING) {
            return true;
        }
        if (job.getStatus() == ReportStatus.RETRY_WAIT) {
            return job.getNextRetryAt() != null && job.getNextRetryAt() <= now;
        }
        return job.getStatus() == ReportStatus.PROCESSING
                && job.getLockedUntil() != null && job.getLockedUntil() <= now;
    }

    private ReportGenerationJob owned(Long id, String workerId) {
        requireWorker(workerId);
        ReportGenerationJob job = lock(id);
        if (job.getStatus() != ReportStatus.PROCESSING
                || !workerId.equals(job.getLockedBy())) {
            throw new ReportOperationConflictException("当前执行器未持有报告任务租约");
        }
        return job;
    }

    private ReportGenerationJob lock(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("报告ID不能为空");
        }
        return repository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
    }

    private int retryDelaySeconds(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 5);
        return 30 * (1 << exponent);
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

    private void requireWorker(String workerId) {
        if (workerId == null || workerId.trim().isEmpty() || workerId.length() > 128) {
            throw new IllegalArgumentException("报告执行器ID不能为空或过长");
        }
    }
}
