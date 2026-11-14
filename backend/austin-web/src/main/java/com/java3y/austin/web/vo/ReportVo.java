package com.java3y.austin.web.vo;

import com.java3y.austin.report.domain.ReportGenerationJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportVo {
    private Long id;
    private String reportType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private Integer attemptCount;
    private Integer replayCount;
    private String filename;
    private Long fileSize;
    private String sha256;
    private String downloadPath;
    private String lastError;
    private Integer created;
    private Integer completedAt;

    public static ReportVo from(ReportGenerationJob job) {
        return ReportVo.builder().id(job.getId()).reportType(job.getReportType().name())
                .periodStart(job.getPeriodStart()).periodEnd(job.getPeriodEnd())
                .status(job.getStatus().name()).attemptCount(job.getAttemptCount())
                .replayCount(job.getReplayCount()).filename(job.getOriginalFilename())
                .fileSize(job.getFileSize()).sha256(job.getFileSha256())
                .downloadPath(job.getCompletedAt() == null ? null
                        : "/billing/reports/" + job.getId() + "/download")
                .lastError(job.getLastError()).created(job.getCreated())
                .completedAt(job.getCompletedAt()).build();
    }
}
