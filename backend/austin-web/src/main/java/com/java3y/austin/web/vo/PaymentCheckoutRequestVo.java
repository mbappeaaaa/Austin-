package com.java3y.austin.web.vo;

import lombok.Data;

@Data
public class PaymentCheckoutRequestVo {
    private String packageCode;
    private String provider;
    private String idempotencyKey;
}
