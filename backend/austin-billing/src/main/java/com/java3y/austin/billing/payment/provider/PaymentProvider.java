package com.java3y.austin.billing.payment.provider;

import com.java3y.austin.billing.payment.domain.PaymentProviderType;

public interface PaymentProvider {
    PaymentProviderType type();
    PaymentCheckoutResult createCheckout(PaymentCheckoutRequest request);
    VerifiedPaymentEvent verifyWebhook(String payload, String signature);

    /**
     * 重新获取支付凭证（如 PaymentIntent 的 client_secret）。
     * 返回 null 表示渠道没有可刷新凭证，调用方应回退到持久化的 checkoutUrl。
     */
    default PaymentCheckoutResult refreshCheckout(PaymentCheckoutRequest request, String providerOrderId) {
        return null;
    }
}
