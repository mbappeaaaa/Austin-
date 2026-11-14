package com.java3y.austin.report.document;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuotaLedgerRow {
    private Long id;
    private String entryType;
    private Long availableDelta;
    private Long reservedDelta;
    private Long availableAfter;
    private Long reservedAfter;
    private String businessType;
    private String businessId;
    private Integer created;
}
