package com.java3y.austin.report.storage;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StoredReport {
    private String provider;
    private String storageKey;
    private Long fileSize;
    private String sha256;
}
