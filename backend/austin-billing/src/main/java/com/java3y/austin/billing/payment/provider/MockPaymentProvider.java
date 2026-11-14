package com.java3y.austin.billing.payment.provider;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

public class MockPaymentProvider implements PaymentProvider {
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300L;

    private final byte[] signingSecret;
    private final String checkoutBaseUrl;
    private final Clock clock;

    public MockPaymentProvider(String signingSecret, String checkoutBaseUrl) {
        this(signingSecret, checkoutBaseUrl, Clock.systemUTC());
    }

    MockPaymentProvider(String signingSecret, String checkoutBaseUrl, Clock clock) {
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new IllegalStateException("Mock支付签名密钥至少需要32位且必须由运行时注入");
        }
        if (checkoutBaseUrl == null || checkoutBaseUrl.trim().isEmpty()) {
            throw new IllegalStateException("Mock支付页面地址不能为空");
        }
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.checkoutBaseUrl = stripTrailingSlash(checkoutBaseUrl);
        this.clock = clock;
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.MOCK;
    }

    @Override
    public PaymentCheckoutResult createCheckout(PaymentCheckoutRequest request) {
        String providerOrderId = "mock_cs_" + DigestUtil.sha256Hex(request.getOrderNo()).substring(0, 24);
        String token = hmacHex(providerOrderId + "|" + request.getOrderNo());
        String url = checkoutBaseUrl + "/billing/payments/mock/" + providerOrderId
                + "?orderNo=" + request.getOrderNo() + "&token=" + token;
        return new PaymentCheckoutResult(providerOrderId, url);
    }

    @Override
    public VerifiedPaymentEvent verifyWebhook(String payload, String signature) {
        verifySignature(payload, signature);
        JSONObject body;
        try {
            body = JSON.parseObject(payload);
        } catch (RuntimeException exception) {
            throw new PaymentProviderException("Mock支付回调内容无效", exception);
        }
        return VerifiedPaymentEvent.builder().provider(type())
                .providerEventId(required(body, "eventId"))
                .providerOrderId(required(body, "providerOrderId"))
                .orderNo(required(body, "orderNo"))
                .eventType(required(body, "eventType"))
                .status(parseStatus(required(body, "status")))
                .amountMinor(body.getLong("amountMinor"))
                .currency(upper(body.getString("currency")))
                .providerCreatedAt(body.getInteger("created"))
                .liveMode(Boolean.TRUE.equals(body.getBoolean("liveMode")))
                .payloadHash(DigestUtil.sha256Hex(payload)).build();
    }

    public boolean verifyCheckoutToken(String providerOrderId, String orderNo, String token) {
        if (token == null) {
            return false;
        }
        byte[] expected = hmacHex(providerOrderId + "|" + orderNo)
                .getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, token.getBytes(StandardCharsets.US_ASCII));
    }

    private void verifySignature(String payload, String signature) {
        try {
            String[] parts = signature == null ? new String[0] : signature.split(",");
            Long timestamp = null;
            String supplied = null;
            for (String part : parts) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2 && "t".equals(pair[0])) {
                    timestamp = Long.valueOf(pair[1]);
                } else if (pair.length == 2 && "v1".equals(pair[0])) {
                    supplied = pair[1];
                }
            }
            long now = Instant.now(clock).getEpochSecond();
            if (timestamp == null || supplied == null
                    || Math.abs(now - timestamp) > SIGNATURE_TOLERANCE_SECONDS) {
                throw new PaymentProviderException("Mock支付回调签名无效或已过期");
            }
            byte[] expected = hmacHex(timestamp + "." + payload).getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.US_ASCII))) {
                throw new PaymentProviderException("Mock支付回调签名无效或已过期");
            }
        } catch (NumberFormatException exception) {
            throw new PaymentProviderException("Mock支付回调签名无效或已过期");
        }
    }

    private String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算Mock支付签名", exception);
        }
    }

    private VerifiedPaymentStatus parseStatus(String value) {
        try {
            return VerifiedPaymentStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return VerifiedPaymentStatus.IRRELEVANT;
        }
    }

    private String required(JSONObject body, String field) {
        String value = body.getString(field);
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentProviderException("Mock支付回调缺少必要字段");
        }
        return value;
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
