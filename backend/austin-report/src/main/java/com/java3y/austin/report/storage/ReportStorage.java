package com.java3y.austin.report.storage;

public interface ReportStorage {
    String provider();

    StoredReport store(Long tenantId, String filename, byte[] content);

    ReportResource load(String storageKey);
}
