package com.java3y.austin.report.service;

import com.java3y.austin.report.storage.ReportResource;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReportDownload {
    private String filename;
    private String contentType;
    private String sha256;
    private ReportResource resource;
}
