package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.report.service.ReportDownload;
import com.java3y.austin.report.service.ReportDownloadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/billing/reports")
@Api("报告文件下载")
public class ReportDownloadController {
    private final ReportDownloadService downloadService;
    private final CurrentUserService currentUserService;

    public ReportDownloadController(ReportDownloadService downloadService,
                                    CurrentUserService currentUserService) {
        this.downloadService = downloadService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{id}/download")
    @ApiOperation("鉴权下载当前租户已完成的Word报告")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        ReportDownload download = downloadService.download(currentUserService.tenantId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .contentLength(download.getResource().getContentLength())
                .cacheControl(CacheControl.noStore())
                .eTag("\"" + download.getSha256() + "\"")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.getFilename() + "\"")
                .body(new InputStreamResource(download.getResource().getInputStream()));
    }
}
