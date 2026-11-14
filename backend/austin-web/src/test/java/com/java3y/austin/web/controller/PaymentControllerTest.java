package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.provider.PaymentCheckoutResult;
import com.java3y.austin.billing.payment.service.PaymentCheckoutOutcome;
import com.java3y.austin.billing.payment.service.PaymentCheckoutService;
import com.java3y.austin.billing.payment.service.PaymentOrderTransactionService;
import com.java3y.austin.web.vo.PaymentCheckoutRequestVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    @Test
    void shouldAlwaysCreateCheckoutInsideCurrentTenant() {
        PaymentCheckoutService checkoutService = mock(PaymentCheckoutService.class);
        PaymentOrderTransactionService transactionService = mock(PaymentOrderTransactionService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(currentUserService.tenantId()).thenReturn(7L);
        PaymentOrder order = PaymentOrder.builder().orderNo("pay-1")
                .provider(PaymentProviderType.STRIPE).packageCode("SANDBOX_STARTER")
                .packageNameSnapshot("Starter").currency("USD").amountMinor(100L)
                .quotaAmount(1000L).status(PaymentOrderStatus.PENDING).build();
        when(checkoutService.checkout(7L, "SANDBOX_STARTER",
                PaymentProviderType.STRIPE, "idem-1")).thenReturn(new PaymentCheckoutOutcome(order,
                new PaymentCheckoutResult("cs_1", "https://checkout.example/pay")));
        PaymentController controller = new PaymentController(
                checkoutService, transactionService, currentUserService);
        PaymentCheckoutRequestVo request = new PaymentCheckoutRequestVo();
        request.setPackageCode("SANDBOX_STARTER");
        request.setProvider("stripe");
        request.setIdempotencyKey("idem-1");

        assertEquals("pay-1", controller.checkout(request).getOrderNo());

        verify(checkoutService).checkout(7L, "SANDBOX_STARTER",
                PaymentProviderType.STRIPE, "idem-1");
    }

    @Test
    void shouldExposeClientSecretForStripeIntentCheckout() {
        PaymentCheckoutService checkoutService = mock(PaymentCheckoutService.class);
        PaymentOrderTransactionService transactionService = mock(PaymentOrderTransactionService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(currentUserService.tenantId()).thenReturn(7L);
        PaymentOrder order = PaymentOrder.builder().orderNo("pay-2")
                .provider(PaymentProviderType.STRIPE_INTENT).packageCode("SANDBOX_STARTER")
                .packageNameSnapshot("Starter").currency("USD").amountMinor(100L)
                .quotaAmount(1000L).status(PaymentOrderStatus.PENDING)
                .providerOrderId("pi_1").build();
        when(checkoutService.checkout(7L, "SANDBOX_STARTER",
                PaymentProviderType.STRIPE_INTENT, "idem-2"))
                .thenReturn(new PaymentCheckoutOutcome(order, new PaymentCheckoutResult(
                        "pi_1", null, "pk_test_pub", "pi_1_secret_dontpanic")));
        PaymentController controller = new PaymentController(
                checkoutService, transactionService, currentUserService);
        PaymentCheckoutRequestVo request = new PaymentCheckoutRequestVo();
        request.setPackageCode("SANDBOX_STARTER");
        request.setProvider("stripe_intent");
        request.setIdempotencyKey("idem-2");

        var vo = controller.checkout(request);

        assertEquals("CLIENT_SECRET", vo.getDisplayMode());
        assertEquals("pk_test_pub", vo.getPublishableKey());
        assertEquals("pi_1_secret_dontpanic", vo.getClientSecret());
    }
}
