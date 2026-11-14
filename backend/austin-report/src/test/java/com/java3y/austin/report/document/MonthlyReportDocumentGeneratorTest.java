package com.java3y.austin.report.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyReportDocumentGeneratorTest {
    @Test
    void shouldGenerateAuditableDocxWithFixedPageAndTableGeometry() throws Exception {
        MonthlyReportData data = sample();
        byte[] documentBytes = new MonthlyReportDocumentGenerator().generate(data);
        Path output = Paths.get("target", "report-qa", "monthly-report-preview.docx");
        Files.createDirectories(output.getParent());
        Files.write(output, documentBytes);

        assertTrue(documentBytes.length > 5000);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            assertEquals(BigInteger.valueOf(12240),
                    document.getDocument().getBody().getSectPr().getPgSz().getW());
            assertEquals(BigInteger.valueOf(1440),
                    document.getDocument().getBody().getSectPr().getPgMar().getLeft());
            assertTrue(document.getParagraphs().stream()
                    .anyMatch(paragraph -> paragraph.getText().contains("Austin 月度消息用量与账务报告")));
            for (XWPFTable table : document.getTables()) {
                assertEquals(BigInteger.valueOf(9360), table.getCTTbl().getTblPr().getTblW().getW());
                BigInteger gridWidth = BigInteger.ZERO;
                for (int index = 0; index < table.getCTTbl().getTblGrid().sizeOfGridColArray(); index++) {
                    gridWidth = gridWidth.add((BigInteger) table.getCTTbl().getTblGrid()
                            .getGridColArray(index).getW());
                }
                assertEquals(BigInteger.valueOf(9360), gridWidth);
            }
        }
    }

    private MonthlyReportData sample() {
        List<QuotaLedgerRow> ledger = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            ledger.add(new QuotaLedgerRow((long) index + 1, index % 3 == 0 ? "SETTLE" : "RESERVE",
                    index % 3 == 0 ? 0L : -1L, index % 3 == 0 ? -1L : 1L,
                    900L - index, 10L + index, "MESSAGE", "1001:" + index,
                    1782864000 + index * 3600));
        }
        return MonthlyReportData.builder().tenantId(7L).tenantName("示例租户")
                .tenantTimezone("Asia/Taipei").periodStart(LocalDate.of(2026, 7, 1))
                .periodEnd(LocalDate.of(2026, 8, 1)).settledUsage(1280L)
                .purchasedQuota(2000L).availableQuota(720L).reservedQuota(8L)
                .templateUsage(Arrays.asList(new TemplateUsageRow("1001", 900L),
                        new TemplateUsageRow("1002", 380L)))
                .feeSummaries(Collections.singletonList(new FeeSummaryRow("USD", 200L, 2L)))
                .paymentDetails(Arrays.asList(
                        new PaymentDetailRow("pay_example_1", "MOCK", "USD", 100L, 1000L, 1782864000),
                        new PaymentDetailRow("pay_example_2", "STRIPE", "USD", 100L, 1000L, 1782950400)))
                .paymentDetailsTruncated(false).quotaLedger(ledger).quotaLedgerTruncated(false).build();
    }
}
