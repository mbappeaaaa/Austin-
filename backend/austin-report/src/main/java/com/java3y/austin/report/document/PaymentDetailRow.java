package com.java3y.austin.report.document;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentDetailRow {
    private String orderNo;
    private String provider;
    private String currency;
    private Long amountMinor;
    private Long quotaAmount;
    private Integer paidAt;
}
