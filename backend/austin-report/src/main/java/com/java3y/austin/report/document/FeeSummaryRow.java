package com.java3y.austin.report.document;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FeeSummaryRow {
    private String currency;
    private Long amountMinor;
    private Long orderCount;
}
