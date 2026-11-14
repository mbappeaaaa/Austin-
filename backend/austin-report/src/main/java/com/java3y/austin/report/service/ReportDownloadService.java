package com.java3y.austin.report.service;

import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.domain.ReportStatus;
import com.java3y.austin.report.storage.ReportResource;
import com.java3y.austin.report.storage.ReportStorageRegistry;
import org.springframework.stereotype.Service;

@Service
public class ReportDownloadService {
    private final ReportRequestService requestService;
    private final ReportStorageRegistry storageRegistry;

    public ReportDownloadService(ReportRequestService requestService,
                                 ReportStorageRegistry storageRegistry) {
        this.requestService = requestService;
        this.storageRegistry = storageRegistry;
    }

    public ReportDownload download(Long tenantId, Long reportId) {
        ReportGenerationJob job = requestService.requireTenantJob(tenantId, reportId);
        if (job.getStatus() != ReportStatus.COMPLETED || job.getStorageKey() == null) {
            throw new ReportOperationConflictException("报告尚未生成完成");
        }
        ReportResource resource = storageRegistry.require(job.getStorageProvider())
                .load(job.getStorageKey());
        if (job.getFileSize() != null && job.getFileSize() != resource.getContentLength()) {
            try {
                resource.getInputStream().close();
            } catch (java.io.IOException ignored) {
                // 不覆盖完整性校验异常。
            }
            throw new IllegalStateException("报告文件大小与元数据不一致");
        }
        return new ReportDownload(job.getOriginalFilename(), job.getContentType(),
                job.getFileSha256(), resource);
    }
}
