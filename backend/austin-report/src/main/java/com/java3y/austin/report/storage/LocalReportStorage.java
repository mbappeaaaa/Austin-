package com.java3y.austin.report.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@ConditionalOnProperty(name = "austin.report.storage.provider", havingValue = "LOCAL",
        matchIfMissing = true)
public class LocalReportStorage implements ReportStorage {
    private final Path baseDirectory;

    public LocalReportStorage(@Value("${austin.report.storage.local.base-dir:${java.io.tmpdir}/austin-reports}")
                              String baseDirectory) {
        this.baseDirectory = java.nio.file.Paths.get(baseDirectory).toAbsolutePath().normalize();
    }

    @Override
    public String provider() {
        return "LOCAL";
    }

    @Override
    public StoredReport store(Long tenantId, String filename, byte[] content) {
        if (tenantId == null || tenantId <= 0 || content == null || content.length == 0) {
            throw new IllegalArgumentException("报告存储参数无效");
        }
        String safeFilename = safeFilename(filename);
        // 任务按租户和月份唯一；确定性Key使崩溃后的重试覆盖同一文件，不累积孤儿对象。
        String key = tenantId + "/" + safeFilename;
        Path target = resolveKey(key);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".report-", ".tmp");
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredReport(provider(), key, (long) content.length, sha256(content));
        } catch (IOException exception) {
            throw new IllegalStateException("保存报告文件失败", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 临时文件由操作系统后续清理；不覆盖主异常。
                }
            }
        }
    }

    @Override
    public ReportResource load(String storageKey) {
        Path target = resolveKey(storageKey);
        try {
            if (!Files.isRegularFile(target)) {
                throw new IllegalArgumentException("报告文件不存在");
            }
            InputStream stream = Files.newInputStream(target);
            return new ReportResource(stream, Files.size(target));
        } catch (IOException exception) {
            throw new IllegalStateException("读取报告文件失败", exception);
        }
    }

    private Path resolveKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("报告存储Key不能为空");
        }
        Path target = baseDirectory.resolve(key).normalize();
        if (!target.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("报告存储Key非法");
        }
        return target;
    }

    private String safeFilename(String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9._-]{1,200}\\.docx")) {
            throw new IllegalArgumentException("报告文件名非法");
        }
        return filename;
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }
}
