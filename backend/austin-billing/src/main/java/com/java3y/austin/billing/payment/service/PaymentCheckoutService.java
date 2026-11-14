package com.java3y.austin.billing.payment.service;

import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.provider.PaymentCheckoutRequest;
import com.java3y.austin.billing.payment.provider.PaymentCheckoutResult;
import com.java3y.austin.billing.payment.provider.PaymentProvider;
import com.java3y.austin.billing.payment.provider.PaymentProviderRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentCheckoutService {
    private final PaymentOrderTransactionService transactionService;
    private final PaymentProviderRegistry providerRegistry;

    @Value("${austin.billing.payment.checkout-success-url}")
    private String successUrl;
    @Value("${austin.billing.payment.checkout-cancel-url}")
    private String cancelUrl;

    public PaymentCheckoutService(PaymentOrderTransactionService transactionService,
                                  PaymentProviderRegistry providerRegistry) {
        this.transactionService = transactionService;
        this.providerRegistry = providerRegistry;
    }

    public PaymentCheckoutOutcome checkout(Long tenantId, String packageCode,
                                           PaymentProviderType providerType, String idempotencyKey) {
        providerRegistry.requireForCheckout(providerType);
        PaymentOrder order = transactionService.findOrCreate(
                tenantId, packageCode, providerType, idempotencyKey);
        return new PaymentCheckoutOutcome(order, resolveResult(order));
    }

    public java.util.List<PaymentProviderType> availableProviders() {
        return providerRegistry.availableTypes();
    }

    public PaymentCheckoutOutcome describe(Long tenantId, String orderNo) {
        PaymentOrder order = transactionService.requireTenantOrder(tenantId, orderNo);
        return new PaymentCheckoutOutcome(order, resolveResult(order));
    }

    private PaymentCheckoutResult resolveResult(PaymentOrder order) {
        if (order.getProvider() == PaymentProviderType.ALIPAY && order.getCheckoutUrl() != null) {
            return new PaymentCheckoutResult(order.getProviderOrderId(), order.getCheckoutUrl());
        }
        if (order.getProviderOrderId() == null) {
            PaymentProvider provider = providerRegistry.require(order.getProvider());
            PaymentCheckoutResult created = provider.createCheckout(checkoutRequest(order));
            transactionService.attachCheckout(order.getOrderNo(), created);
            return created;
        }
        if (order.getStatus() == PaymentOrderStatus.CREATED
                || order.getStatus() == PaymentOrderStatus.PENDING) {
            PaymentCheckoutResult refreshed = providerRegistry.require(order.getProvider())
                    .refreshCheckout(checkoutRequest(order), order.getProviderOrderId());
            if (refreshed != null) {
                return refreshed;
            }
        }
        return new PaymentCheckoutResult(order.getProviderOrderId(), order.getCheckoutUrl());
    }

    private PaymentCheckoutRequest checkoutRequest(PaymentOrder order) {
        return PaymentCheckoutRequest.builder()
                .tenantId(order.getTenantId()).orderNo(order.getOrderNo())
                .packageName(order.getPackageNameSnapshot()).currency(order.getCurrency())
                .amountMinor(order.getAmountMinor()).quotaAmount(order.getQuotaAmount())
                .successUrl(successUrl).cancelUrl(cancelUrl).build();
    }
}
