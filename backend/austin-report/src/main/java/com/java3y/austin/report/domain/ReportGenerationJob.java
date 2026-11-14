package com.java3y.austin.report.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "report_generation_job", uniqueConstraints =
        @UniqueConstraint(name = "uk_report_tenant_type_period",
                columnNames = {"tenant_id", "report_type", "period_start", "period_end"}))
public class ReportGenerationJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 64)
    private ReportType reportType;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportStatus status;
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;
    @Column(name = "replay_count", nullable = false)
    private Integer replayCount;
    @Column(name = "next_retry_at")
    private Integer nextRetryAt;
    @Column(name = "locked_by", length = 128)
    private String lockedBy;
    @Column(name = "locked_until")
    private Integer lockedUntil;
    @Column(name = "storage_provider", length = 32)
    private String storageProvider;
    @Column(name = "storage_key", length = 512)
    private String storageKey;
    @Column(name = "original_filename", length = 255)
    private String originalFilename;
    @Column(name = "content_type", length = 128)
    private String contentType;
    @Column(name = "file_size")
    private Long fileSize;
    @Column(name = "file_sha256", length = 64)
    private String fileSha256;
    @Column(name = "last_error", length = 512)
    private String lastError;
    @Column(name = "started_at")
    private Integer startedAt;
    @Column(name = "completed_at")
    private Integer completedAt;
    @Column(nullable = false)
    private Integer created;
    @Column(nullable = false)
    private Integer updated;
    @Version
    @Column(nullable = false)
    private Long version;
}
