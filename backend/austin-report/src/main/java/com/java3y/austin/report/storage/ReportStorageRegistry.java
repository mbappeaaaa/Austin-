package com.java3y.austin.report.storage;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportStorageRegistry {
    private final Map<String, ReportStorage> providers = new HashMap<>();

    public ReportStorageRegistry(List<ReportStorage> storages) {
        for (ReportStorage storage : storages) {
            ReportStorage previous = providers.put(storage.provider(), storage);
            if (previous != null) {
                throw new IllegalStateException("报告存储Provider重复: " + storage.provider());
            }
        }
    }

    public ReportStorage require(String provider) {
        ReportStorage storage = providers.get(provider);
        if (storage == null) {
            throw new IllegalStateException("未配置报告存储Provider: " + provider);
        }
        return storage;
    }
}
