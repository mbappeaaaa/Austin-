package com.java3y.austin.billing.payment.provider;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StripeIntentPaymentProviderTest {

    @Test
    void shouldVerifySignatureAndMapSucceededPaymentIntent() throws Exception {
        String apiKey = "sk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String webhookSecret = UUID.randomUUID() + UUID.randomUUID().toString();
        StripeIntentPaymentProvider provider = new StripeIntentPaymentProvider(
                new StripeClient(apiKey), "pk_test_pub", webhookSecret);
        long now = Instant.now().getEpochSecond();
        String payload = intentEvent(now, Stripe.API_VERSION, "payment_intent.succeeded", "succeeded");
        String signature = "t=" + now + ",v1=" + hmac(webhookSecret, now + "." + payload);

        VerifiedPaymentEvent event = provider.verifyWebhook(payload, signature);

        assertEquals(VerifiedPaymentStatus.SUCCEEDED, event.getStatus());
        assertEquals("pay-1", event.getOrderNo());
        assertEquals(100L, event.getAmountMinor());
        assertEquals("USD", event.getCurrency());
        assertEquals("pi_test_runtime", event.getProviderOrderId());
        assertFalse(event.isLiveMode());
        assertThrows(PaymentProviderException.class,
                () -> provider.verifyWebhook(payload, signature + "bad"));
    }

    @Test
    void shouldDeserializeEventWhenApiVersionMismatchesSdk() throws Exception {
        // Stripe账户/CLI可能使用比SDK锁定版本更新的API版本,此时getObject()返回empty,
        // 渠道必须降级到deserializeUnsafe()而不是拒绝合法回调
        String apiKey = "sk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String webhookSecret = UUID.randomUUID() + UUID.randomUUID().toString();
        StripeIntentPaymentProvider provider = new StripeIntentPaymentProvider(
                new StripeClient(apiKey), "pk_test_pub", webhookSecret);
        long now = Instant.now().getEpochSecond();
        String payload = intentEvent(now, "2099-01-01.future-version", "payment_intent.succeeded", "succeeded");
        String signature = "t=" + now + ",v1=" + hmac(webhookSecret, now + "." + payload);

        VerifiedPaymentEvent event = provider.verifyWebhook(payload, signature);

        assertEquals(VerifiedPaymentStatus.SUCCEEDED, event.getStatus());
        assertEquals("pay-1", event.getOrderNo());
        assertEquals(100L, event.getAmountMinor());
    }

    @Test
    void shouldMapCanceledIntentToExpiredAndKeepFailedIntentPending() throws Exception {
        String apiKey = "sk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String webhookSecret = UUID.randomUUID() + UUID.randomUUID().toString();
        StripeIntentPaymentProvider provider = new StripeIntentPaymentProvider(
                new StripeClient(apiKey), "pk_test_pub", webhookSecret);
        long now = Instant.now().getEpochSecond();

        String canceledPayload = intentEvent(now, Stripe.API_VERSION, "payment_intent.canceled", "canceled");
        VerifiedPaymentEvent canceled = provider.verifyWebhook(canceledPayload,
                "t=" + now + ",v1=" + hmac(webhookSecret, now + "." + canceledPayload));
        assertEquals(VerifiedPaymentStatus.EXPIRED, canceled.getStatus());

        String failedPayload = intentEvent(now, Stripe.API_VERSION, "payment_intent.payment_failed",
                "requires_payment_method");
        VerifiedPaymentEvent failed = provider.verifyWebhook(failedPayload,
                "t=" + now + ",v1=" + hmac(webhookSecret, now + "." + failedPayload));
        // 支付失败保持"等待支付"，允许换支付方式重试，不关单
        assertEquals(VerifiedPaymentStatus.IRRELEVANT, failed.getStatus());
    }

    @Test
    void shouldIgnoreNonPaymentIntentEvents() throws Exception {
        String apiKey = "sk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String webhookSecret = UUID.randomUUID() + UUID.randomUUID().toString();
        StripeIntentPaymentProvider provider = new StripeIntentPaymentProvider(
                new StripeClient(apiKey), "pk_test_pub", webhookSecret);
        long now = Instant.now().getEpochSecond();

        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("id", "ch_1");
        charge.put("object", "charge");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("object", charge);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt_charge");
        event.put("object", "event");
        event.put("api_version", Stripe.API_VERSION);
        event.put("created", now);
        event.put("livemode", false);
        event.put("type", "charge.succeeded");
        event.put("data", data);
        String payload = JSON.toJSONString(event);

        VerifiedPaymentEvent verified = provider.verifyWebhook(payload,
                "t=" + now + ",v1=" + hmac(webhookSecret, now + "." + payload));

        assertEquals(VerifiedPaymentStatus.IRRELEVANT, verified.getStatus());
        assertNull(verified.getOrderNo());
    }

    @Test
    void shouldRejectIncompleteConfiguration() {
        String apiKey = "sk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        StripeClient client = new StripeClient(apiKey);

        assertThrows(IllegalStateException.class,
                () -> new StripeIntentPaymentProvider(client, null, "whsec_x"));
        assertThrows(IllegalStateException.class,
                () -> new StripeIntentPaymentProvider(client, "  ", "whsec_x"));
        assertThrows(IllegalStateException.class,
                () -> new StripeIntentPaymentProvider(client, "pk_test_pub", null));
        assertThrows(IllegalStateException.class,
                () -> new StripeIntentPaymentProvider(client, "pk_test_pub", ""));
        assertThrows(IllegalStateException.class,
                () -> new StripeIntentPaymentProvider(null, "pk_test_pub", "whsec_x"));
    }

    private String intentEvent(long now, String apiVersion, String type, String intentStatus) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("order_no", "pay-1");
        metadata.put("tenant_id", "7");
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("id", "pi_test_runtime");
        intent.put("object", "payment_intent");
        intent.put("amount", 100L);
        intent.put("currency", "usd");
        intent.put("status", intentStatus);
        intent.put("livemode", false);
        intent.put("created", now);
        intent.put("metadata", metadata);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("object", intent);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt_runtime_" + type);
        event.put("object", "event");
        event.put("api_version", apiVersion);
        event.put("created", now);
        event.put("livemode", false);
        event.put("type", type);
        event.put("data", data);
        return JSON.toJSONString(event);
    }

    private String hmac(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder result = new StringBuilder();
        for (byte item : mac.doFinal(value.getBytes(StandardCharsets.UTF_8))) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }
}
