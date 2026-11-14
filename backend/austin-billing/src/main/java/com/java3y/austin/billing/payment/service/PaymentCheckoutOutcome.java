package com.java3y.austin.billing.payment.service;

import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.provider.PaymentCheckoutResult;
import lombok.Value;

@Value
public class PaymentCheckoutOutcome {
    PaymentOrder order;
    PaymentCheckoutResult result;
}
