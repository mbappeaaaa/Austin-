package com.java3y.austin.report.service;

import com.java3y.austin.report.domain.ReportGenerationJob;
import org.springframework.stereotype.Service;

@Service
public class ReportAdminService {
    private final ReportJobStateService stateService;
    private final ReportGenerationService generationService;

    public ReportAdminService(ReportJobStateService stateService,
                              ReportGenerationService generationService) {
        this.stateService = stateService;
        this.generationService = generationService;
    }

    public boolean replay(Long tenantId, Long reportId, int leaseSeconds) {
        ReportGenerationJob job = stateService.replayDead(tenantId, reportId);
        return generationService.generate(job.getId(), leaseSeconds);
    }
}
