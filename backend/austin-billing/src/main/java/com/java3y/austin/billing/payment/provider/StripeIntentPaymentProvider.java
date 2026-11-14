package com.java3y.austin.billing.payment.provider;

import cn.hutool.crypto.digest.DigestUtil;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;
import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

import java.util.Locale;

/**
 * Stripe PaymentIntent 渠道（stripe_intent）。
 * 与旧的 Checkout Session 渠道（stripe）互相独立：回调事件、返回凭证字段完全不同，
 * 按支付接入规范禁止复用或迁移旧实现。
 */
public class StripeIntentPaymentProvider implements PaymentProvider {
    private final StripeClient stripeClient;
    private final String publishableKey;
    private final String webhookSecret;

    public StripeIntentPaymentProvider(StripeClient stripeClient,
                                       String publishableKey, String webhookSecret) {
        if (stripeClient == null) {
            throw new IllegalStateException("StripeClient必须由运行时注入");
        }
        if (publishableKey == null || publishableKey.trim().isEmpty()) {
            throw new IllegalStateException("Stripe Publishable Key必须由运行时注入");
        }
        if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
            throw new IllegalStateException("Stripe Webhook签名密钥必须由运行时注入");
        }
        this.stripeClient = stripeClient;
        this.publishableKey = publishableKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.STRIPE_INTENT;
    }

    @Override
    public PaymentCheckoutResult createCheckout(PaymentCheckoutRequest request) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(request.getAmountMinor())
                .setCurrency(request.getCurrency().toLowerCase(Locale.ROOT))
                .setDescription(request.getPackageName())
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true).build())
                .putMetadata("order_no", request.getOrderNo())
                .putMetadata("tenant_id", String.valueOf(request.getTenantId()))
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("intent:" + request.getOrderNo()).build();
        try {
            PaymentIntent intent = stripeClient.v1().paymentIntents().create(params, options);
            if (Boolean.TRUE.equals(intent.getLivemode())) {
                throw new PaymentProviderException("Sandbox版本拒绝Stripe Live PaymentIntent");
            }
            return new PaymentCheckoutResult(intent.getId(), null, publishableKey, intent.getClientSecret());
        } catch (StripeException exception) {
            throw new PaymentProviderException("Stripe PaymentIntent创建失败", exception);
        }
    }

    @Override
    public PaymentCheckoutResult refreshCheckout(PaymentCheckoutRequest request, String providerOrderId) {
        try {
            PaymentIntent intent = stripeClient.v1().paymentIntents().retrieve(providerOrderId);
            if (Boolean.TRUE.equals(intent.getLivemode())) {
                throw new PaymentProviderException("Sandbox版本拒绝Stripe Live PaymentIntent");
            }
            return new PaymentCheckoutResult(intent.getId(), null, publishableKey, intent.getClientSecret());
        } catch (StripeException exception) {
            throw new PaymentProviderException("Stripe PaymentIntent查询失败", exception);
        }
    }

    @Override
    public VerifiedPaymentEvent verifyWebhook(String payload, String signature) {
        final Event event;
        try {
            event = stripeClient.constructEvent(payload, signature, webhookSecret);
        } catch (RuntimeException | com.stripe.exception.SignatureVerificationException exception) {
            throw new PaymentProviderException("Stripe Webhook验签失败", exception);
        }
        StripeObject object;
        try {
            // Stripe账户/CLI可能使用比SDK锁定版本更新的API版本,getObject()在版本不一致时返回empty,
            // 按官方建议降级到deserializeUnsafe();webhook核心字段(id/amount/currency/status/metadata)跨版本稳定
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            object = deserializer.getObject().orElseGet(() -> {
                try {
                    return deserializer.deserializeUnsafe();
                } catch (EventDataObjectDeserializationException exception) {
                    throw new PaymentProviderException("Stripe Webhook对象无法解析", exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception instanceof PaymentProviderException) {
                throw exception;
            }
            throw new PaymentProviderException("Stripe Webhook对象无法解析", exception);
        }
        if (!(object instanceof PaymentIntent)) {
            return baseEvent(event).status(VerifiedPaymentStatus.IRRELEVANT).build();
        }
        PaymentIntent intent = (PaymentIntent) object;
        return baseEvent(event).providerOrderId(intent.getId())
                .orderNo(intent.getMetadata() == null ? null : intent.getMetadata().get("order_no"))
                .status(mapStatus(event.getType(), intent.getStatus()))
                .amountMinor(intent.getAmount()).currency(upper(intent.getCurrency())).build();
    }

    private VerifiedPaymentStatus mapStatus(String eventType, String intentStatus) {
        if ("payment_intent.succeeded".equals(eventType)
                && "succeeded".equals(intentStatus)) {
            return VerifiedPaymentStatus.SUCCEEDED;
        }
        if ("payment_intent.canceled".equals(eventType)) {
            return VerifiedPaymentStatus.EXPIRED;
        }
        // payment_failed 保持"等待支付"，允许用户换支付方式重试；其余事件验签后忽略并回200
        return VerifiedPaymentStatus.IRRELEVANT;
    }

    private VerifiedPaymentEvent.VerifiedPaymentEventBuilder baseEvent(Event event) {
        return VerifiedPaymentEvent.builder().provider(type()).providerEventId(event.getId())
                .eventType(event.getType()).providerCreatedAt(toInteger(event.getCreated()))
                .liveMode(Boolean.TRUE.equals(event.getLivemode()))
                .payloadHash(DigestUtil.sha256Hex(event.getLastResponse().body()));
    }

    private Integer toInteger(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
