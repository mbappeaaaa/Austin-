package com.java3y.austin.billing.payment.config;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.java3y.austin.billing.payment.provider.AlipayPaymentProvider;
import com.java3y.austin.billing.payment.provider.MockPaymentProvider;
import com.java3y.austin.billing.payment.provider.StripeIntentPaymentProvider;
import com.java3y.austin.billing.payment.provider.StripePaymentProvider;
import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class PaymentProviderConfig {

    @Bean
    @Profile({"local", "dev", "test"})
    @ConditionalOnProperty(name = "austin.billing.payment.mock.enabled", havingValue = "true")
    public MockPaymentProvider mockPaymentProvider(
            @Value("${austin.billing.payment.mock.signing-secret}") String signingSecret,
            @Value("${austin.billing.payment.mock.checkout-base-url}") String checkoutBaseUrl) {
        return new MockPaymentProvider(signingSecret, checkoutBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "austin.billing.payment.stripe.enabled", havingValue = "true")
    public StripePaymentProvider stripePaymentProvider(
            @Value("${austin.billing.payment.stripe.api-key}") String apiKey,
            @Value("${austin.billing.payment.stripe.webhook-secret}") String webhookSecret) {
        requireSandboxStripeKey(apiKey);
        return new StripePaymentProvider(new StripeClient(apiKey), webhookSecret);
    }

    @Bean
    @ConditionalOnProperty(name = "austin.billing.payment.alipay.enabled", havingValue = "true")
    public AlipayPaymentProvider alipayPaymentProvider(
            @Value("${austin.billing.payment.alipay.gateway-url}") String gatewayUrl,
            @Value("${austin.billing.payment.alipay.app-id}") String appId,
            @Value("${austin.billing.payment.alipay.app-private-key}") String appPrivateKey,
            @Value("${austin.billing.payment.alipay.alipay-public-key}") String alipayPublicKey,
            @Value("${austin.billing.payment.alipay.notify-url}") String notifyUrl,
            @Value("${austin.billing.payment.alipay.checkout-base-url}") String checkoutBaseUrl) {
        requireSandboxAlipayGateway(gatewayUrl);
        AlipayConfig alipayConfig = new AlipayConfig();
        alipayConfig.setServerUrl(gatewayUrl);
        alipayConfig.setAppId(appId);
        alipayConfig.setPrivateKey(appPrivateKey);
        alipayConfig.setFormat("JSON");
        alipayConfig.setAlipayPublicKey(alipayPublicKey);
        alipayConfig.setCharset("UTF-8");
        alipayConfig.setSignType("RSA2");
        AlipayClient alipayClient;
        try {
            alipayClient = new DefaultAlipayClient(alipayConfig);
        } catch (AlipayApiException exception) {
            throw new IllegalStateException("支付宝沙箱AlipayClient初始化失败", exception);
        }
        return new AlipayPaymentProvider(alipayClient, appId, alipayPublicKey,
                notifyUrl, checkoutBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "austin.billing.payment.stripe-intent.enabled", havingValue = "true")
    public StripeIntentPaymentProvider stripeIntentPaymentProvider(
            @Value("${austin.billing.payment.stripe-intent.api-key}") String apiKey,
            @Value("${austin.billing.payment.stripe-intent.publishable-key}") String publishableKey,
            @Value("${austin.billing.payment.stripe-intent.webhook-secret}") String webhookSecret) {
        requireSandboxStripeIntentKeys(apiKey, publishableKey, webhookSecret);
        return new StripeIntentPaymentProvider(new StripeClient(apiKey), publishableKey, webhookSecret);
    }

    static void requireSandboxStripeKey(String apiKey) {
        if (apiKey == null || !(apiKey.startsWith("rk_test_") || apiKey.startsWith("sk_test_"))) {
            throw new IllegalStateException("Sandbox版本只接受Stripe测试模式API Key，优先使用最小权限rk_test_密钥");
        }
        if (apiKey.startsWith("sk_test_")) {
            throw new IllegalStateException("Stripe必须使用最小权限rk_test_受限密钥");
        }
    }

    static void requireSandboxAlipayGateway(String gatewayUrl) {
        if (!"https://openapi-sandbox.dl.alipaydev.com/gateway.do".equals(gatewayUrl)
                && !"https://openapi.alipaydev.com/gateway.do".equals(gatewayUrl)) {
            throw new IllegalStateException("Sandbox版本只接受支付宝沙箱网关地址");
        }
    }
    static void requireSandboxStripeIntentKeys(String apiKey, String publishableKey, String webhookSecret) {
        if (apiKey == null || !(apiKey.startsWith("rk_test_") || apiKey.startsWith("sk_test_"))) {
            throw new IllegalStateException("Sandbox版本只接受Stripe测试模式API Key（sk_test_或rk_test_），拒绝一切Live密钥");
        }
        if (publishableKey == null || !publishableKey.startsWith("pk_test_")) {
            throw new IllegalStateException("Stripe Intent渠道必须配置测试模式Publishable Key（pk_test_）");
        }
        if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
            throw new IllegalStateException("Stripe Webhook签名密钥必须由运行时注入");
        }
    }
}
