package com.java3y.austin.billing.payment.provider;

import com.java3y.austin.billing.payment.domain.PaymentProviderType;

public class PaymentProviderUnavailableException extends IllegalStateException {
    public PaymentProviderUnavailableException(PaymentProviderType type) {
        super("支付渠道未启用（" + type + "），请联系管理员完成配置或选择其他支付方式");
    }
}
