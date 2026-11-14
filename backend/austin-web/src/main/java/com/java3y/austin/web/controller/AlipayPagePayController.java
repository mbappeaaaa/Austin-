package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.provider.AlipayPaymentProvider;
import com.java3y.austin.billing.payment.provider.PaymentProviderRegistry;
import com.java3y.austin.billing.payment.service.PaymentOrderTransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 支付宝电脑网站支付跳转页：checkout返回的checkoutUrl指向这里，
 * 页面内渲染自动提交的支付宝支付表单。
 */
@RestController
public class AlipayPagePayController {

    private final PaymentProviderRegistry providerRegistry;
    private final PaymentOrderTransactionService transactionService;
    private final CurrentUserService currentUserService;

    @Value("${austin.billing.payment.checkout-success-url}")
    private String successUrl;

    public AlipayPagePayController(PaymentProviderRegistry providerRegistry,
                                   PaymentOrderTransactionService transactionService,
                                   CurrentUserService currentUserService) {
        this.providerRegistry = providerRegistry;
        this.transactionService = transactionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping(value = "/billing/payments/alipay/redirect/{orderNo}",
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> redirect(@PathVariable String orderNo) {
        PaymentOrder order = transactionService.requireTenantOrder(
                currentUserService.tenantId(), orderNo);
        if (order.getProvider() != PaymentProviderType.ALIPAY) {
            return ResponseEntity.badRequest().body("Not an alipay order");
        }
        if (order.getStatus() != PaymentOrderStatus.CREATED
                && order.getStatus() != PaymentOrderStatus.PENDING) {
            return ResponseEntity.badRequest()
                    .body("Order is not payable, status: " + order.getStatus());
        }
        PaymentProviderType providerType = PaymentProviderType.ALIPAY;
        if (!(providerRegistry.require(providerType) instanceof AlipayPaymentProvider)) {
            return ResponseEntity.status(503).body("Alipay provider is unavailable");
        }
        AlipayPaymentProvider provider =
                (AlipayPaymentProvider) providerRegistry.require(providerType);
        String form = provider.buildPagePayForm(orderNo, order.getPackageNameSnapshot(),
                order.getAmountMinor(), successUrl + (successUrl.contains("?") ? "&" : "?") + "orderNo=" + orderNo);
        String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<title>Alipay Sandbox Checkout</title></head><body>" + form + "</body></html>";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }
}
