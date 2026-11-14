package com.java3y.austin.report.service;

import com.java3y.austin.report.document.MonthlyReportData;
import com.java3y.austin.report.document.MonthlyReportDataAssembler;
import com.java3y.austin.report.document.MonthlyReportDocumentGenerator;
import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.domain.ReportStatus;
import com.java3y.austin.report.storage.ReportStorage;
import com.java3y.austin.report.storage.ReportStorageRegistry;
import com.java3y.austin.report.storage.StoredReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ReportGenerationService {
    private final ReportJobStateService stateService;
    private final MonthlyReportDataAssembler assembler;
    private final MonthlyReportDocumentGenerator generator;
    private final ReportStorageRegistry storageRegistry;
    private final String workerId = "report-" + UUID.randomUUID().toString();

    @Value("${austin.report.storage.provider:LOCAL}")
    private String storageProvider;

    public ReportGenerationService(ReportJobStateService stateService,
                                   MonthlyReportDataAssembler assembler,
                                   MonthlyReportDocumentGenerator generator,
                                   ReportStorageRegistry storageRegistry) {
        this.stateService = stateService;
        this.assembler = assembler;
        this.generator = generator;
        this.storageRegistry = storageRegistry;
    }

    public boolean generate(Long id, int leaseSeconds) {
        Optional<ReportGenerationJob> claimed = stateService.claim(id, workerId, leaseSeconds);
        if (!claimed.isPresent()) {
            return false;
        }
        ReportGenerationJob job = claimed.get();
        try {
            MonthlyReportData data = assembler.assemble(job.getTenantId(),
                    job.getPeriodStart(), job.getPeriodEnd());
            byte[] content = generator.generate(data);
            String filename = "austin-monthly-" + job.getTenantId() + "-"
                    + java.time.YearMonth.from(job.getPeriodStart()) + ".docx";
            ReportStorage storage = storageRegistry.require(storageProvider);
            StoredReport stored = storage.store(job.getTenantId(), filename, content);
            stateService.complete(job.getId(), workerId, stored, filename,
                    MonthlyReportDocumentGenerator.DOCX_CONTENT_TYPE);
            return true;
        } catch (RuntimeException exception) {
            ReportStatus status = stateService.failed(job.getId(), workerId, exception);
            log.error("Monthly report generation failed, reportId:{}, status:{}, failureType:{}",
                    job.getId(), status, exception.getClass().getSimpleName());
            return false;
        }
    }
}
