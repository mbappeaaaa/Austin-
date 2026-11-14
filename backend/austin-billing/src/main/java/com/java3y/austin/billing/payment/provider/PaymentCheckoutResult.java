package com.java3y.austin.billing.payment.provider;

import lombok.Value;

@Value
public class PaymentCheckoutResult {
    String providerOrderId;
    String checkoutUrl;
    String publishableKey;
    String clientSecret;

    public PaymentCheckoutResult(String providerOrderId, String checkoutUrl) {
        this(providerOrderId, checkoutUrl, null, null);
    }

    public PaymentCheckoutResult(String providerOrderId, String checkoutUrl,
                                 String publishableKey, String clientSecret) {
        this.providerOrderId = providerOrderId;
        this.checkoutUrl = checkoutUrl;
        this.publishableKey = publishableKey;
        this.clientSecret = clientSecret;
    }
}
