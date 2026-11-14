package com.java3y.austin.billing.payment.service;

import lombok.Value;

@Value
public class PaymentWebhookResult {
    boolean duplicate;
    boolean stateChanged;
    String orderNo;
}
