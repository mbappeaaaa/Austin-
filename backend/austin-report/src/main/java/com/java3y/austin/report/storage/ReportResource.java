package com.java3y.austin.report.storage;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;

@Data
@AllArgsConstructor
public class ReportResource {
    private InputStream inputStream;
    private long contentLength;
}
