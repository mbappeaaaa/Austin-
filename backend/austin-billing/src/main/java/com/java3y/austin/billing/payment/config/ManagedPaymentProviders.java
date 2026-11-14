package com.java3y.austin.billing.payment.config;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.provider.PaymentProvider;
import com.java3y.austin.billing.payment.repository.PaymentOrderRepository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.net.URI;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

@Service
public class ManagedPaymentProviders {
    private final PaymentChannelRepository repository;
    private final PaymentConfigCipher cipher;
    private final Environment environment;
    private final PaymentOrderRepository orders;

    public ManagedPaymentProviders(PaymentChannelRepository repository, PaymentConfigCipher cipher,
                                   Environment environment, PaymentOrderRepository orders) {
        this.repository = repository;
        this.cipher = cipher;
        this.environment = environment;
        this.orders = orders;
    }

    @Getter @Setter
    public static class Update {
        private boolean enabled;
        private BigDecimal feeRate = BigDecimal.ZERO;
        private String remark = "";
        private Long version;
        private Map<String, String> fields = new LinkedHashMap<>();
    }

    @Getter
    public static class View {
        private String provider;
        private boolean enabled;
        private boolean configured;
        private String source;
        private BigDecimal feeRate;
        private String remark;
        private Long version;
        private Map<String, String> fields;
        private Set<String> savedSecrets;
    }

    public Optional<PaymentChannelConfig> find(PaymentProviderType type) {
        return repository.findById(type.name());
    }

    public List<View> list() {
        List<View> result = new ArrayList<>();
        for (PaymentProviderType type : PaymentProviderType.values()) result.add(view(type, find(type).orElse(null)));
        return result;
    }

    private static Map<String, String> fieldProperties(PaymentProviderType type) {
        Map<String, String> fields = new LinkedHashMap<>();
        switch (type) {
            case STRIPE: case STRIPE_INTENT:
                fields.put("apiKey", "api-key");
                fields.put("webhookSecret", "webhook-secret");
                if (type == PaymentProviderType.STRIPE_INTENT) fields.put("publishableKey", "publishable-key");
                break;
            case ALIPAY:
                fields.put("appId", "app-id");
                fields.put("appPrivateKey", "app-private-key");
                fields.put("alipayPublicKey", "alipay-public-key");
                fields.put("gatewayUrl", "gateway-url");
                fields.put("notifyUrl", "notify-url");
                fields.put("checkoutBaseUrl", "checkout-base-url");
                break;
            case MOCK:
                fields.put("signingSecret", "signing-secret");
                fields.put("checkoutBaseUrl", "checkout-base-url");
                break;
            default: throw new IllegalArgumentException("不支持此支付渠道");
        }
        return fields;
    }

    private static boolean secret(String name) {
        return new HashSet<>(Arrays.asList("apiKey", "webhookSecret", "appPrivateKey", "alipayPublicKey", "signingSecret")).contains(name);
    }

    private String prefix(PaymentProviderType type) {
        return "austin.billing.payment." + type.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".";
    }

    private Map<String, String> fields(PaymentProviderType type, PaymentChannelConfig config) {
        Map<String, String> result = new LinkedHashMap<>();
        if (config != null) {
            JSON.parseObject(cipher.decrypt(type.name(), config.getEncryptedConfig()))
                    .forEach((key, value) -> result.put(key, String.valueOf(value)));
            return result;
        }
        fieldProperties(type).forEach((name, prop) -> {
            String value;
            try { value = environment.getProperty(prefix(type) + prop, ""); }
            catch (IllegalArgumentException unresolvedPlaceholder) { value = ""; }
            result.put(name, value);
        });
        if (type == PaymentProviderType.ALIPAY) {
            result.putIfAbsent("gatewayUrl", "https://openapi-sandbox.dl.alipaydev.com/gateway.do");
            // Redirect via the frontend proxy so the browser uses the same session cookie.
            if (result.getOrDefault("checkoutBaseUrl", "").trim().isEmpty()) result.put("checkoutBaseUrl", "http://localhost:3000/api");
        }
        return result;
    }

    private View view(PaymentProviderType type, PaymentChannelConfig config) {
        Map<String, String> values = fields(type, config);
        View view = new View();
        view.provider = type.name();
        view.enabled = config != null ? config.isEnabled() : environment.getProperty(prefix(type) + "enabled", Boolean.class, false);
        view.configured = fieldProperties(type).keySet().stream().allMatch(k -> !values.getOrDefault(k, "").trim().isEmpty());
        view.source = config == null ? "ENVIRONMENT" : "DATABASE";
        view.feeRate = config == null ? BigDecimal.ZERO : config.getFeeRate();
        view.remark = config == null ? "" : config.getRemark();
        view.version = config == null ? null : config.getVersion();
        view.fields = new LinkedHashMap<>();
        view.savedSecrets = new LinkedHashSet<>();
        values.forEach((name, value) -> {
            if (secret(name)) { if (!value.trim().isEmpty()) view.savedSecrets.add(name); }
            else view.fields.put(name, value);
        });
        return view;
    }

    @Transactional
    public View save(PaymentProviderType type, Update update, String actor) {
        PaymentChannelConfig config = find(type).orElse(null);
        if (!Objects.equals(config == null ? null : config.getVersion(), update.version))
            throw new IllegalArgumentException("配置已被其他管理员修改，请刷新后重试");
        if (update.feeRate == null || update.feeRate.signum() < 0 || update.feeRate.compareTo(new BigDecimal("100")) > 0
                || update.feeRate.scale() > 4) throw new IllegalArgumentException("手续费率必须在0到100之间，最多4位小数");
        if (update.remark == null || update.remark.length() > 255) throw new IllegalArgumentException("备注不能超过255字");
        if (update.fields == null) throw new IllegalArgumentException("缺少渠道配置");
        Map<String, String> previous = fields(type, config);
        Map<String, String> merged = new LinkedHashMap<>(previous);
        update.fields.forEach((name, value) -> {
            if (!fieldProperties(type).containsKey(name)) throw new IllegalArgumentException("包含不支持的配置字段");
            if (value == null || value.length() > 16384) throw new IllegalArgumentException("配置字段无效或过长");
            // Blank secret inputs mean keep the saved value; a mask is never a credential.
            if (secret(name) && value.trim().isEmpty()) return;
            if (secret(name) && value.contains("****")) throw new IllegalArgumentException("请填写新的密钥，或留空保留原值");
            merged.put(name, value.trim());
        });
        if (!previous.equals(merged) && orders.existsByProviderAndStatusIn(type,
                Arrays.asList(PaymentOrderStatus.CREATED, PaymentOrderStatus.PENDING)))
            throw new IllegalArgumentException("此渠道存在未完成订单，请先处理订单再更换密钥或应用；可先关闭新支付");
        if (update.enabled) build(type, merged);
        if (config == null) { config = new PaymentChannelConfig(); config.setProvider(type.name()); }
        config.setEnabled(update.enabled);
        config.setFeeRate(update.feeRate);
        config.setRemark(update.remark);
        config.setEncryptedConfig(cipher.encrypt(type.name(), JSON.toJSONString(merged)));
        config.setUpdatedAt(Instant.now());
        config.setUpdatedBy(actor);
        return view(type, repository.saveAndFlush(config));
    }

    public PaymentProvider provider(PaymentProviderType type, PaymentChannelConfig config) {
        return build(type, fields(type, config));
    }

    private PaymentProvider build(PaymentProviderType type, Map<String, String> f) {
        for (String required : fieldProperties(type).keySet())
            if (f.getOrDefault(required, "").trim().isEmpty()) throw new IllegalArgumentException("请完整填写渠道配置：" + required);
        PaymentProviderConfig factory = new PaymentProviderConfig();
        try {
            switch (type) {
                case STRIPE:
                    requireWebhook(f.get("webhookSecret"));
                    return factory.stripePaymentProvider(f.get("apiKey"), f.get("webhookSecret"));
                case STRIPE_INTENT:
                    requireWebhook(f.get("webhookSecret"));
                    return factory.stripeIntentPaymentProvider(f.get("apiKey"), f.get("publishableKey"), f.get("webhookSecret"));
                case ALIPAY:
                    if (!f.get("appId").matches("[0-9]{16}")) throw new IllegalArgumentException("支付宝App ID应为16位数字");
                    validateRsa(f);
                    httpUrl(f.get("notifyUrl"));
                    httpUrl(f.get("checkoutBaseUrl"));
                    return factory.alipayPaymentProvider(f.get("gatewayUrl"), f.get("appId"), normalizePem(f.get("appPrivateKey")),
                            normalizePem(f.get("alipayPublicKey")), f.get("notifyUrl"), f.get("checkoutBaseUrl").replaceAll("/+$", ""));
                case MOCK:
                    if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local", "dev", "test")))
                        throw new IllegalArgumentException("Mock仅允许在本地或测试环境使用");
                    httpUrl(f.get("checkoutBaseUrl"));
                    return factory.mockPaymentProvider(f.get("signingSecret"), f.get("checkoutBaseUrl"));
                default: throw new IllegalArgumentException("不支持此渠道");
            }
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("渠道配置无效：请检查沙箱环境、密钥格式与必填字段");
        }
    }

    private void requireWebhook(String value) {
        if (!value.startsWith("whsec_")) throw new IllegalArgumentException("Webhook签名密钥必须以whsec_开头");
    }
    private void httpUrl(String value) {
        try {
            URI url = URI.create(value);
            if (!Arrays.asList("https", "http").contains(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null
                    || url.getFragment() != null || url.getQuery() != null) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new IllegalArgumentException("请输入有效的HTTP(S)地址，不得包含账号、查询参数或片段"); }
    }
    private String normalizePem(String value) {
        return value.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
    }
    private void validateRsa(Map<String, String> f) {
        try {
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            java.security.interfaces.RSAKey privateKey = (java.security.interfaces.RSAKey) rsa.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalizePem(f.get("appPrivateKey")))));
            java.security.interfaces.RSAKey publicKey = (java.security.interfaces.RSAKey) rsa.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(normalizePem(f.get("alipayPublicKey")))));
            if (privateKey.getModulus().bitLength() < 2048 || publicKey.getModulus().bitLength() < 2048) throw new IllegalArgumentException();
        } catch (Exception e) { throw new IllegalArgumentException("支付宝需要RSA2密钥：PKCS8应用私钥和支付宝公钥，至少2048位"); }
    }
}
