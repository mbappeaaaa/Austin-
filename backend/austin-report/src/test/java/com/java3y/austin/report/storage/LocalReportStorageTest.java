package com.java3y.austin.report.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalReportStorageTest {
    @TempDir
    Path directory;

    @Test
    void shouldStoreDeterministicallyAndRejectPathTraversal() throws Exception {
        LocalReportStorage storage = new LocalReportStorage(directory.toString());
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second".getBytes(StandardCharsets.UTF_8);
        StoredReport stored = storage.store(7L, "austin-monthly-7-2026-07.docx", first);
        storage.store(7L, "austin-monthly-7-2026-07.docx", second);

        assertEquals("7/austin-monthly-7-2026-07.docx", stored.getStorageKey());
        ReportResource resource = storage.load(stored.getStorageKey());
        try (java.io.InputStream input = resource.getInputStream()) {
            assertArrayEquals(second, readAll(input));
        }
        assertThrows(IllegalArgumentException.class, () -> storage.load("../secret"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(7L, "../../evil.docx", first));
    }

    private byte[] readAll(java.io.InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
