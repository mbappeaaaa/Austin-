package com.java3y.austin.billing.payment.config;

import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.repository.PaymentOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedPaymentProvidersTest {
    @TempDir Path directory;
    private final PaymentChannelRepository repository = mock(PaymentChannelRepository.class);
    private final PaymentOrderRepository orders = mock(PaymentOrderRepository.class);
    private final MockEnvironment env = new MockEnvironment();
    private ManagedPaymentProviders service() {
        env.setActiveProfiles("dev");
        return new ManagedPaymentProviders(repository, new PaymentConfigCipher("", directory.resolve("key").toString()), env, orders);
    }
    private ManagedPaymentProviders.Update stripe() {
        ManagedPaymentProviders.Update update = new ManagedPaymentProviders.Update();
        update.setEnabled(true);
        Map<String, String> fields = new HashMap<>();
        fields.put("apiKey", "sk_test_" + UUID.randomUUID());
        fields.put("publishableKey", "pk_test_" + UUID.randomUUID());
        fields.put("webhookSecret", "whsec_" + UUID.randomUUID());
        update.setFields(fields);
        return update;
    }
    @Test
    void secretsStayEncryptedAndBlankInputsRetainThem() {
        ManagedPaymentProviders svc = service();
        ManagedPaymentProviders.Update update = stripe();
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            PaymentChannelConfig saved = call.getArgument(0);
            saved.setVersion(0L);
            when(repository.findById("STRIPE_INTENT")).thenReturn(Optional.of(saved));
            return saved;
        });
        ManagedPaymentProviders.View view = svc.save(PaymentProviderType.STRIPE_INTENT, update, "admin");
        assertFalse(view.getFields().containsKey("apiKey"));
        assertTrue(view.getSavedSecrets().contains("apiKey"));
        PaymentChannelConfig saved = repository.findById("STRIPE_INTENT").get();
        assertFalse(saved.getEncryptedConfig().contains(update.getFields().get("apiKey")));
        ManagedPaymentProviders.Update disable = new ManagedPaymentProviders.Update();
        disable.setVersion(0L);
        disable.setFields(Collections.singletonMap("apiKey", ""));
        assertFalse(svc.save(PaymentProviderType.STRIPE_INTENT, disable, "admin").isEnabled());
        // Disabled channels retain provider credentials for already-paid order callbacks.
        assertNotNull(svc.provider(PaymentProviderType.STRIPE_INTENT, saved));
    }
    @Test
    void invalidKeysAndStaleVersionsCannotEnableChannel() {
        ManagedPaymentProviders.Update update = stripe();
        update.getFields().put("apiKey", "sk_live_not_allowed");
        assertThrows(IllegalArgumentException.class, () -> service().save(PaymentProviderType.STRIPE_INTENT, update, "admin"));
        update.setVersion(99L);
        assertThrows(IllegalArgumentException.class, () -> service().save(PaymentProviderType.STRIPE_INTENT, update, "admin"));
        verify(repository, never()).saveAndFlush(any());
    }
    @Test
    void pendingOrdersBlockCredentialReplacement() {
        when(orders.existsByProviderAndStatusIn(eq(PaymentProviderType.STRIPE_INTENT), anyList())).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service().save(PaymentProviderType.STRIPE_INTENT, stripe(), "admin"));
        verify(repository, never()).saveAndFlush(any());
    }
    @Test
    void keySurvivesRestartAndCiphertextCannotMoveBetweenChannels() {
        String path = directory.resolve("persistent-key").toString();
        String encrypted = new PaymentConfigCipher("", path).encrypt("STRIPE", "test-secret");
        PaymentConfigCipher restarted = new PaymentConfigCipher("", path);
        assertEquals("test-secret", restarted.decrypt("STRIPE", encrypted));
        assertThrows(IllegalStateException.class, () -> restarted.decrypt("ALIPAY", encrypted));
    }
    @Test
    void alipayGatewayMustBeExactSandboxHost() {
        assertDoesNotThrow(() -> PaymentProviderConfig.requireSandboxAlipayGateway("https://openapi-sandbox.dl.alipaydev.com/gateway.do"));
        assertThrows(IllegalStateException.class, () -> PaymentProviderConfig.requireSandboxAlipayGateway("https://alipaydev.com.attacker.invalid/gateway.do"));
        assertThrows(IllegalStateException.class, () -> PaymentProviderConfig.requireSandboxAlipayGateway("https://openapi.alipay.com/gateway.do"));
    }
}
