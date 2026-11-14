package com.java3y.austin.billing.payment.domain;

import java.util.Locale;

public enum PaymentProviderType {
    MOCK,
    STRIPE,
    ALIPAY,
    STRIPE_INTENT;

    public static PaymentProviderType parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("支付提供方不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的支付提供方: " + value);
        }
    }
}
