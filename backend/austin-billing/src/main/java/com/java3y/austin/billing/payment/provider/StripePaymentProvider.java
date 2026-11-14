package com.java3y.austin.billing.payment.provider;

import cn.hutool.crypto.digest.DigestUtil;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;

import java.util.Locale;

public class StripePaymentProvider implements PaymentProvider {
    private final StripeClient stripeClient;
    private final String webhookSecret;

    public StripePaymentProvider(StripeClient stripeClient, String webhookSecret) {
        if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
            throw new IllegalStateException("Stripe Webhook签名密钥必须由运行时注入");
        }
        this.stripeClient = stripeClient;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.STRIPE;
    }

    @Override
    public PaymentCheckoutResult createCheckout(PaymentCheckoutRequest request) {
        SessionCreateParams.LineItem.PriceData.ProductData product =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(request.getPackageName()).build();
        SessionCreateParams.LineItem.PriceData price =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(request.getCurrency().toLowerCase(Locale.ROOT))
                        .setUnitAmount(request.getAmountMinor()).setProductData(product).build();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(request.getSuccessUrl())
                .setCancelUrl(request.getCancelUrl())
                .setClientReferenceId(request.getOrderNo())
                .setIntegrationIdentifier("austin_quota_" + integrationSuffix(request.getOrderNo()))
                .putMetadata("order_no", request.getOrderNo())
                .putMetadata("tenant_id", String.valueOf(request.getTenantId()))
                .addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L)
                        .setPriceData(price).build())
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("checkout:" + request.getOrderNo()).build();
        try {
            Session session = stripeClient.v1().checkout().sessions().create(params, options);
            if (Boolean.TRUE.equals(session.getLivemode())) {
                throw new PaymentProviderException("Sandbox版本拒绝Stripe Live Checkout Session");
            }
            return new PaymentCheckoutResult(session.getId(), session.getUrl());
        } catch (StripeException exception) {
            throw new PaymentProviderException("Stripe Checkout Session创建失败", exception);
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
        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new PaymentProviderException("Stripe Webhook对象无法解析"));
        if (!(object instanceof Session)) {
            return baseEvent(event).status(VerifiedPaymentStatus.IRRELEVANT).build();
        }
        Session session = (Session) object;
        VerifiedPaymentStatus status = mapStatus(event.getType(), session.getPaymentStatus());
        return baseEvent(event).providerOrderId(session.getId())
                .orderNo(session.getClientReferenceId()).status(status)
                .amountMinor(session.getAmountTotal()).currency(upper(session.getCurrency())).build();
    }

    private VerifiedPaymentEvent.VerifiedPaymentEventBuilder baseEvent(Event event) {
        return VerifiedPaymentEvent.builder().provider(type()).providerEventId(event.getId())
                .eventType(event.getType()).providerCreatedAt(toInteger(event.getCreated()))
                .liveMode(Boolean.TRUE.equals(event.getLivemode()))
                .payloadHash(DigestUtil.sha256Hex(event.getLastResponse().body()));
    }

    private VerifiedPaymentStatus mapStatus(String eventType, String paymentStatus) {
        if (("checkout.session.completed".equals(eventType)
                || "checkout.session.async_payment_succeeded".equals(eventType))
                && "paid".equals(paymentStatus)) {
            return VerifiedPaymentStatus.SUCCEEDED;
        }
        if ("checkout.session.async_payment_failed".equals(eventType)) {
            return VerifiedPaymentStatus.FAILED;
        }
        if ("checkout.session.expired".equals(eventType)) {
            return VerifiedPaymentStatus.EXPIRED;
        }
        return VerifiedPaymentStatus.IRRELEVANT;
    }

    private String integrationSuffix(String orderNo) {
        String hash = DigestUtil.sha256Hex(orderNo);
        StringBuilder suffix = new StringBuilder(8);
        for (int index = 0; index < 8; index++) {
            suffix.append((char) ('a' + Integer.parseInt(hash.substring(index * 2, index * 2 + 2), 16) % 26));
        }
        return suffix.toString();
    }

    private Integer toInteger(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
