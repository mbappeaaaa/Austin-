package com.java3y.austin.billing.payment.service;

import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.provider.PaymentProviderRegistry;
import com.java3y.austin.billing.payment.provider.PaymentProviderUnavailableException;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentChannelAvailabilityTest {
    @Test
    void disabledChannelMustNotCreateAnOrder() {
        PaymentOrderTransactionService transactions = mock(PaymentOrderTransactionService.class);
        PaymentCheckoutService checkout = new PaymentCheckoutService(transactions,
                new PaymentProviderRegistry(Collections.emptyList()));
        assertTrue(checkout.availableProviders().isEmpty());
        assertThrows(PaymentProviderUnavailableException.class,
                () -> checkout.checkout(1L, "SANDBOX_STARTER", PaymentProviderType.STRIPE_INTENT, "key"));
        verifyNoInteractions(transactions);
    }
}
