package com.java3y.austin.report.service;

import com.java3y.austin.report.domain.ReportStatus;
import com.java3y.austin.report.repository.ReportGenerationJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "austin.report.generation.enabled", havingValue = "true",
        matchIfMissing = true)
public class ReportJobScheduler {
    private final ReportGenerationJobRepository repository;
    private final ReportGenerationService generationService;

    @Value("${austin.report.scan-batch-size:10}")
    private int scanBatchSize;
    @Value("${austin.report.lease-seconds:300}")
    private int leaseSeconds;

    public ReportJobScheduler(ReportGenerationJobRepository repository,
                              ReportGenerationService generationService) {
        this.repository = repository;
        this.generationService = generationService;
    }

    @Scheduled(fixedDelayString = "${austin.report.scan-interval-ms:10000}")
    public void generateReadyReports() {
        int batchSize = Math.max(1, Math.min(scanBatchSize, 100));
        List<Long> ids = repository.findReadyIds(ReportStatus.PENDING, ReportStatus.RETRY_WAIT,
                ReportStatus.PROCESSING, Math.toIntExact(Instant.now().getEpochSecond()),
                PageRequest.of(0, batchSize));
        for (Long id : ids) {
            try {
                generationService.generate(id, leaseSeconds);
            } catch (RuntimeException exception) {
                log.error("Report scheduler failed, reportId:{}, failureType:{}",
                        id, exception.getClass().getSimpleName());
            }
        }
    }
}
