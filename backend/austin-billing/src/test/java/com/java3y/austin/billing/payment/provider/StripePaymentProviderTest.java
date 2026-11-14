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
import static org.junit.jupiter.api.Assertions.assertThrows;

class StripePaymentProviderTest {

    @Test
    void shouldVerifyStripeSignatureAndMapPaidCheckout() throws Exception {
        String apiKey = "rk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String webhookSecret = UUID.randomUUID().toString() + UUID.randomUUID();
        StripePaymentProvider provider = new StripePaymentProvider(
                new StripeClient(apiKey), webhookSecret);
        long now = Instant.now().getEpochSecond();
        String payload = checkoutEvent(now);
        String signature = "t=" + now + ",v1=" + hmac(webhookSecret, now + "." + payload);

        VerifiedPaymentEvent event = provider.verifyWebhook(payload, signature);

        assertEquals(VerifiedPaymentStatus.SUCCEEDED, event.getStatus());
        assertEquals("pay-1", event.getOrderNo());
        assertEquals(100L, event.getAmountMinor());
        assertEquals("USD", event.getCurrency());
        assertFalse(event.isLiveMode());
        assertThrows(PaymentProviderException.class,
                () -> provider.verifyWebhook(payload, signature + "bad"));
    }

    private String checkoutEvent(long now) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", "cs_test_runtime");
        session.put("object", "checkout.session");
        session.put("client_reference_id", "pay-1");
        session.put("amount_total", 100L);
        session.put("currency", "usd");
        session.put("livemode", false);
        session.put("payment_status", "paid");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("object", session);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt_runtime");
        event.put("object", "event");
        event.put("api_version", Stripe.API_VERSION);
        event.put("created", now);
        event.put("livemode", false);
        event.put("type", "checkout.session.completed");
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
