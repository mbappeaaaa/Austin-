package com.java3y.austin.billing.payment.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentProviderConfigTest {

    @Test
    void shouldOnlyAcceptRestrictedStripeSandboxKey() {
        String restrictedTestKey = "rk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String broadTestKey = "sk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String restrictedLiveKey = "rk_" + "live_" + UUID.randomUUID().toString().replace("-", "");

        assertDoesNotThrow(() -> PaymentProviderConfig.requireSandboxStripeKey(restrictedTestKey));
        assertThrows(IllegalStateException.class,
                () -> PaymentProviderConfig.requireSandboxStripeKey(broadTestKey));
        assertThrows(IllegalStateException.class,
                () -> PaymentProviderConfig.requireSandboxStripeKey(restrictedLiveKey));
    }

    @Test
    void shouldAcceptTestModeKeysForIntentChannel() {
        String secretTestKey = "sk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String restrictedTestKey = "rk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String publishableKey = "pk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String webhookSecret = "whsec_" + UUID.randomUUID();

        assertDoesNotThrow(() -> PaymentProviderConfig.requireSandboxStripeIntentKeys(
                secretTestKey, publishableKey, webhookSecret));
        assertDoesNotThrow(() -> PaymentProviderConfig.requireSandboxStripeIntentKeys(
                restrictedTestKey, publishableKey, webhookSecret));
    }

    @Test
    void shouldRejectLiveOrIncompleteIntentChannelKeys() {
        String secretTestKey = "sk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String secretLiveKey = "sk_" + "live_" + UUID.randomUUID().toString().replace("-", "");
        String publishableKey = "pk_" + "test_" + UUID.randomUUID().toString().replace("-", "");
        String publishableLiveKey = "pk_" + "live_" + UUID.randomUUID().toString().replace("-", "");

        assertThrows(IllegalStateException.class,
                () -> PaymentProviderConfig.requireSandboxStripeIntentKeys(
                        null, publishableKey, "whsec_1"));
        assertThrows(IllegalStateException.class,
                () -> PaymentProviderConfig.requireSandboxStripeIntentKeys(
                        secretLiveKey, publishableKey, "whsec_1"));
        assertThrows(IllegalStateException.class,
                () -> PaymentProviderConfig.requireSandboxStripeIntentKeys(
                        secretTestKey, null, "whsec_1"));
        assertThrows(IllegalStateException.class,
                () -> PaymentProviderConfig.requireSandboxStripeIntentKeys(
                        secretTestKey, publishableLiveKey, "whsec_1"));
        assertThrows(IllegalStateException.class,
                () -> PaymentProviderConfig.requireSandboxStripeIntentKeys(
                        secretTestKey, publishableKey, null));
        assertThrows(IllegalStateException.class,
                () -> PaymentProviderConfig.requireSandboxStripeIntentKeys(
                        secretTestKey, publishableKey, "  "));
    }
}
