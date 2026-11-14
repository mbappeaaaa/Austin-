package com.java3y.austin.web.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Locale;
import java.util.Map;

/**
 * 渠道账号配置脱敏。列表接口绝不能把凭据原样返回给浏览器。
 */
public final class SensitiveConfigUtils {

    public static final String MASK = "******";

    private SensitiveConfigUtils() {
    }

    public static String mask(String accountConfig) {
        if (accountConfig == null) {
            return null;
        }
        try {
            Object value = JSON.parse(accountConfig);
            maskValue(value);
            return JSON.toJSONString(value);
        } catch (Exception e) {
            return "[配置格式无效，内容已隐藏]";
        }
    }

    /**
     * 编辑页会回传脱敏占位符；将占位符恢复为数据库中的原值，避免把真实凭据覆盖成 ******。
     */
    public static String restoreMasked(String incomingConfig, String storedConfig) {
        if (incomingConfig == null || storedConfig == null || !incomingConfig.contains(MASK)) {
            return incomingConfig;
        }
        try {
            Object incoming = JSON.parse(incomingConfig);
            Object stored = JSON.parse(storedConfig);
            restoreValue(incoming, stored);
            return JSON.toJSONString(incoming);
        } catch (Exception e) {
            return incomingConfig;
        }
    }

    /** 返回仅用于日志序列化的深拷贝，绝不修改 Controller 收到的真实参数。 */
    public static Object sanitizeForLog(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Object copy = JSON.parse(JSON.toJSONString(value));
            maskValue(copy);
            return copy;
        } catch (Exception e) {
            return "[参数无法安全序列化，已隐藏]";
        }
    }

    private static void maskValue(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                if ("accountconfig".equals(normalize(entry.getKey())) && entry.getValue() instanceof String) {
                    entry.setValue(mask((String) entry.getValue()));
                } else if ("headers".equals(normalize(entry.getKey())) && entry.getValue() instanceof JSONObject) {
                    JSONObject headers = (JSONObject) entry.getValue();
                    for (Map.Entry<String, Object> header : headers.entrySet()) {
                        header.setValue(MASK);
                    }
                } else if (isSensitive(entry.getKey())) {
                    entry.setValue(MASK);
                } else {
                    maskValue(entry.getValue());
                }
            }
        } else if (value instanceof JSONArray) {
            for (Object item : (JSONArray) value) {
                maskValue(item);
            }
        }
    }

    private static void restoreValue(Object incoming, Object stored) {
        if (incoming instanceof JSONObject && stored instanceof JSONObject) {
            JSONObject incomingObject = (JSONObject) incoming;
            JSONObject storedObject = (JSONObject) stored;
            for (Map.Entry<String, Object> entry : incomingObject.entrySet()) {
                Object storedValue = storedObject.get(entry.getKey());
                if (MASK.equals(entry.getValue()) && storedValue != null) {
                    entry.setValue(storedValue);
                } else {
                    restoreValue(entry.getValue(), storedValue);
                }
            }
        } else if (incoming instanceof JSONArray && stored instanceof JSONArray) {
            JSONArray incomingArray = (JSONArray) incoming;
            JSONArray storedArray = (JSONArray) stored;
            for (int i = 0; i < Math.min(incomingArray.size(), storedArray.size()); i++) {
                restoreValue(incomingArray.get(i), storedArray.get(i));
            }
        }
    }

    static boolean isSensitive(String key) {
        String normalized = normalize(key);
        return normalized.contains("pass")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.contains("token")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("signature")
                || normalized.contains("webhook")
                || normalized.contains("privatekey")
                || normalized.contains("aeskey");
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
