package com.java3y.austin.billing.payment.provider;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockPaymentProviderTest {
    private static final long NOW = 1_800_000_000L;

    @Test
    void shouldVerifySignedSandboxWebhookAndRejectTampering() throws Exception {
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        MockPaymentProvider provider = new MockPaymentProvider(secret, "http://localhost:8080",
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", "evt-1");
        body.put("providerOrderId", "session-1");
        body.put("orderNo", "pay-1");
        body.put("eventType", "mock.checkout.succeeded");
        body.put("status", "SUCCEEDED");
        body.put("amountMinor", 100L);
        body.put("currency", "usd");
        body.put("created", NOW);
        body.put("liveMode", false);
        String payload = JSON.toJSONString(body);
        String signature = "t=" + NOW + ",v1=" + hmac(secret, NOW + "." + payload);

        VerifiedPaymentEvent event = provider.verifyWebhook(payload, signature);

        assertEquals(VerifiedPaymentStatus.SUCCEEDED, event.getStatus());
        assertEquals("USD", event.getCurrency());
        assertThrows(PaymentProviderException.class,
                () -> provider.verifyWebhook(payload + " ", signature));
    }

    @Test
    void shouldReturnSameCheckoutForProviderRetry() {
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        MockPaymentProvider provider = new MockPaymentProvider(secret, "http://localhost:8080");
        PaymentCheckoutRequest request = PaymentCheckoutRequest.builder().tenantId(7L)
                .orderNo("pay-1").packageName("Starter").currency("USD")
                .amountMinor(100L).quotaAmount(1000L).build();

        assertEquals(provider.createCheckout(request), provider.createCheckout(request));
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
