package com.java3y.austin.web.controller;

import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.service.MockPaymentCheckoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@Profile({"local", "dev", "test"})
@RequestMapping("/billing/payments/mock")
public class MockPaymentCheckoutController {
    private final MockPaymentCheckoutService checkoutService;

    @Value("${austin.billing.payment.checkout-success-url}")
    private String successUrl;

    public MockPaymentCheckoutController(MockPaymentCheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping(value = "/{providerOrderId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String providerOrderId,
                                       @RequestParam String orderNo,
                                       @RequestParam String token) {
        try {
            PaymentOrder order = checkoutService.requireValidOrder(providerOrderId, orderNo, token);
            String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Austin Sandbox Checkout</title>"
                    + "</head><body><h1>Sandbox Checkout</h1><p>Order: " + orderNo
                    + "</p><p>Amount: " + order.getAmountMinor() + " " + order.getCurrency()
                    + "</p><p>Quota: " + order.getQuotaAmount() + "</p><form method=\"post\" action=\"/billing/payments/mock/"
                    + providerOrderId + "\">"
                    + "<input type=\"hidden\" name=\"orderNo\" value=\"" + orderNo + "\">"
                    + "<input type=\"hidden\" name=\"token\" value=\"" + token + "\">"
                    + "<button type=\"submit\">Complete sandbox payment</button></form></body></html>";
            return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                    .body(html);
        } catch (RuntimeException invalid) {
            return ResponseEntity.badRequest().body("Invalid sandbox checkout");
        }
    }

    @PostMapping("/{providerOrderId}")
    public ResponseEntity<Void> confirm(@PathVariable String providerOrderId,
                                        @RequestParam String orderNo,
                                        @RequestParam String token) {
        try {
            checkoutService.confirm(providerOrderId, orderNo, token);
            String separator = successUrl.contains("?") ? "&" : "?";
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION,
                    URI.create(successUrl + separator + "orderNo=" + orderNo).toString()).build();
        } catch (RuntimeException invalid) {
            log.warn("Mock confirm rejected: {}", invalid.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
