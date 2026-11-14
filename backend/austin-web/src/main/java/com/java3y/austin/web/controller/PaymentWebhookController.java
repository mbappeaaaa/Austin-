package com.java3y.austin.web.controller;

import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.provider.PaymentProviderException;
import com.java3y.austin.billing.payment.service.PaymentOperationConflictException;
import com.java3y.austin.billing.payment.service.PaymentWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 支付回调入口。
 * 必须从 HttpServletRequest 读取原始字节:项目注册的 FastJsonHttpMessageConverter 会把
 * @RequestBody String 的 JSON 重新序列化(压缩且键序重排),导致 Stripe 等基于原文签名的验签永远失败。
 */
@Slf4j
@RestController
@RequestMapping("/billing/payments/webhooks")
public class PaymentWebhookController {
    private final PaymentWebhookService webhookService;

    public PaymentWebhookController(PaymentWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<String> webhook(@PathVariable String provider,
                                        HttpServletRequest request,
                                        @RequestHeader(value = "Stripe-Signature", required = false)
                                                String stripeSignature,
                                        @RequestHeader(value = "X-Austin-Mock-Signature", required = false)
                                                String mockSignature) throws IOException {
        String payload = new String(StreamUtils.copyToByteArray(request.getInputStream()),
                StandardCharsets.UTF_8);
        if (payload.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            PaymentProviderType providerType = PaymentProviderType.parse(provider);
            String signature = isStripeFamily(providerType)
                    ? stripeSignature : mockSignature;
            webhookService.receive(providerType, payload, signature);
            if (providerType == PaymentProviderType.ALIPAY) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("success");
            }
            return ResponseEntity.ok().build();
        } catch (PaymentProviderException | PaymentOperationConflictException
                 | IllegalArgumentException invalidCallback) {
            log.warn("Rejected payment webhook, provider:{}, type:{}, detail:{}",
                    provider, invalidCallback.getClass().getSimpleName(), invalidCallback.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException retryableFailure) {
            log.error("Payment webhook processing failed, provider:{}, type:{}",
                    provider, retryableFailure.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private boolean isStripeFamily(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.STRIPE
                || providerType == PaymentProviderType.STRIPE_INTENT;
    }
}
