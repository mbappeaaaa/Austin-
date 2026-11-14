package com.java3y.austin.web.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.java3y.austin.support.domain.ChannelAccount;

class SensitiveConfigUtilsTest {

    @Test
    void shouldMaskPasswordsSecretsApiKeysAndWebhookTokensRecursively() {
        String source = "{\"user\":\"dev@example.com\",\"pass\":\"mail-pass\","
                + "\"secretKey\":\"secret-value\",\"apikey\":\"api-value\","
                + "\"nested\":{\"webhook\":\"https://example.test/hook/token-value\"}}";

        String masked = SensitiveConfigUtils.mask(source);

        assertTrue(masked.contains("dev@example.com"));
        assertFalse(masked.contains("mail-pass"));
        assertFalse(masked.contains("secret-value"));
        assertFalse(masked.contains("api-value"));
        assertFalse(masked.contains("token-value"));
        assertTrue(masked.contains("******"));
    }

    @Test
    void shouldRestoreMaskedValuesWhenEditingWithoutExposingThem() {
        String stored = "{\"webhook\":\"https://example.test/secret-hook\","
                + "\"headers\":{\"Authorization\":\"Bearer real-secret\",\"X-Custom\":\"also-secret\"}}";
        String masked = SensitiveConfigUtils.mask(stored);

        String restored = SensitiveConfigUtils.restoreMasked(masked, stored);

        JSONObject result = JSON.parseObject(restored);
        assertEquals("https://example.test/secret-hook", result.getString("webhook"));
        assertEquals("Bearer real-secret", result.getJSONObject("headers").getString("Authorization"));
        assertEquals("also-secret", result.getJSONObject("headers").getString("X-Custom"));
        assertFalse(masked.contains("real-secret"));
        assertFalse(masked.contains("also-secret"));
    }

    @Test
    void shouldSanitizeNestedAccountConfigForRequestLogsWithoutMutatingRequest() {
        String config = "{\"pass\":\"mail-secret\",\"webhook\":\"https://example.test/hook-secret\"}";
        ChannelAccount request = ChannelAccount.builder().id(1L).accountConfig(config).build();

        Object safe = SensitiveConfigUtils.sanitizeForLog(request);
        String safeJson = JSON.toJSONString(safe);

        assertFalse(safeJson.contains("mail-secret"));
        assertFalse(safeJson.contains("hook-secret"));
        assertEquals(config, request.getAccountConfig());
    }
}
