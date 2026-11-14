package com.java3y.austin.billing.payment.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;
import com.java3y.austin.billing.payment.provider.MockPaymentProvider;
import com.java3y.austin.billing.payment.provider.PaymentProvider;
import com.java3y.austin.billing.payment.provider.PaymentProviderRegistry;
import com.java3y.austin.billing.payment.provider.VerifiedPaymentEvent;
import com.java3y.austin.billing.payment.repository.PaymentOrderRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Service
@Profile({"local", "dev", "test"})
public class MockPaymentCheckoutService {
    private final PaymentOrderRepository orderRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentWebhookProcessor webhookProcessor;

    public MockPaymentCheckoutService(PaymentOrderRepository orderRepository,
                                      PaymentProviderRegistry providerRegistry,
                                      PaymentWebhookProcessor webhookProcessor) {
        this.orderRepository = orderRepository;
        this.providerRegistry = providerRegistry;
        this.webhookProcessor = webhookProcessor;
    }

    public PaymentOrder requireValidOrder(String providerOrderId, String orderNo, String token) {
        PaymentOrder order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("Mock支付订单不存在"));
        if (order.getProvider() != PaymentProviderType.MOCK
                || !Objects.equals(order.getProviderOrderId(), providerOrderId)) {
            throw new PaymentOperationConflictException("Mock支付订单信息不匹配");
        }
        PaymentProvider provider = providerRegistry.require(PaymentProviderType.MOCK);
        MockPaymentProvider mock = (MockPaymentProvider) provider;
        if (!mock.verifyCheckoutToken(providerOrderId, orderNo, token)) {
            throw new PaymentOperationConflictException("Mock支付页面签名无效");
        }
        return order;
    }

    public PaymentWebhookResult confirm(String providerOrderId, String orderNo, String token) {
        PaymentOrder order = requireValidOrder(providerOrderId, orderNo, token);
        int now = Math.toIntExact(Instant.now().getEpochSecond());
        String eventIdentity = "mock-paid|" + providerOrderId + "|" + orderNo;
        VerifiedPaymentEvent event = VerifiedPaymentEvent.builder()
                .provider(PaymentProviderType.MOCK)
                .providerEventId("mock_evt_" + DigestUtil.sha256Hex(eventIdentity).substring(0, 32))
                .providerOrderId(providerOrderId).orderNo(orderNo)
                .eventType("mock.checkout.succeeded").status(VerifiedPaymentStatus.SUCCEEDED)
                .amountMinor(order.getAmountMinor()).currency(order.getCurrency())
                .providerCreatedAt(now).liveMode(false)
                .payloadHash(DigestUtil.sha256Hex(eventIdentity)).build();
        return webhookProcessor.process(event);
    }
}
