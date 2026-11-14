package com.java3y.austin.billing.payment.service;

import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.provider.PaymentProviderRegistry;
import com.java3y.austin.billing.payment.provider.VerifiedPaymentEvent;
import org.springframework.stereotype.Service;

@Service
public class PaymentWebhookService {
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentWebhookProcessor processor;

    public PaymentWebhookService(PaymentProviderRegistry providerRegistry,
                                 PaymentWebhookProcessor processor) {
        this.providerRegistry = providerRegistry;
        this.processor = processor;
    }

    public PaymentWebhookResult receive(PaymentProviderType providerType,
                                        String payload, String signature) {
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("支付回调内容不能为空");
        }
        VerifiedPaymentEvent verified = providerRegistry.require(providerType)
                .verifyWebhook(payload, signature);
        return processor.process(verified);
    }
}
