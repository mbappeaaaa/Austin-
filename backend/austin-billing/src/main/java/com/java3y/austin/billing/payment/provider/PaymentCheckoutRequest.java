package com.java3y.austin.billing.payment.provider;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentCheckoutRequest {
    Long tenantId;
    String orderNo;
    String packageName;
    String currency;
    long amountMinor;
    long quotaAmount;
    String successUrl;
    String cancelUrl;
}
