package com.java3y.austin.report.service;

import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.domain.ReportStatus;
import com.java3y.austin.report.storage.ReportResource;
import com.java3y.austin.report.storage.ReportStorage;
import com.java3y.austin.report.storage.ReportStorageRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportDownloadServiceTest {
    @Test
    void shouldResolveOnlyTenantScopedCompletedReport() {
        ReportRequestService requests = mock(ReportRequestService.class);
        ReportStorageRegistry storages = mock(ReportStorageRegistry.class);
        ReportStorage storage = mock(ReportStorage.class);
        ReportGenerationJob job = ReportGenerationJob.builder().id(11L).tenantId(7L)
                .status(ReportStatus.COMPLETED).storageProvider("LOCAL")
                .storageKey("7/report.docx").originalFilename("report.docx")
                .contentType("application/docx").fileSize(3L).fileSha256("abc").build();
        when(requests.requireTenantJob(7L, 11L)).thenReturn(job);
        when(storages.require("LOCAL")).thenReturn(storage);
        when(storage.load("7/report.docx")).thenReturn(
                new ReportResource(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3));
        ReportDownloadService service = new ReportDownloadService(requests, storages);

        assertEquals("report.docx", service.download(7L, 11L).getFilename());
        verify(requests).requireTenantJob(7L, 11L);
    }
}
