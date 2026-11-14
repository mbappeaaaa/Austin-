package com.java3y.austin.report.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportData {
    private Long tenantId;
    private String tenantName;
    private String tenantTimezone;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Long settledUsage;
    private Long purchasedQuota;
    private Long availableQuota;
    private Long reservedQuota;
    private List<TemplateUsageRow> templateUsage;
    private List<FeeSummaryRow> feeSummaries;
    private List<PaymentDetailRow> paymentDetails;
    private boolean paymentDetailsTruncated;
    private List<QuotaLedgerRow> quotaLedger;
    private boolean quotaLedgerTruncated;
}
