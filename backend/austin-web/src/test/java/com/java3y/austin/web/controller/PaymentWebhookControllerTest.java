package com.java3y.austin.web.controller;

import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.provider.PaymentProviderException;
import com.java3y.austin.billing.payment.service.PaymentWebhookResult;
import com.java3y.austin.billing.payment.service.PaymentWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentWebhookControllerTest {

    private MockHttpServletRequest rawRequest(String payload, String stripeSignature, String mockSignature) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/json");
        request.setContent(payload.getBytes(StandardCharsets.UTF_8));
        if (stripeSignature != null) {
            request.addHeader("Stripe-Signature", stripeSignature);
        }
        if (mockSignature != null) {
            request.addHeader("X-Austin-Mock-Signature", mockSignature);
        }
        return request;
    }

    @Test
    void shouldReturnOkAfterVerifiedCallback() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        when(service.receive(PaymentProviderType.STRIPE, "payload", "signature"))
                .thenReturn(new PaymentWebhookResult(false, true, "pay-1"));
        PaymentWebhookController controller = new PaymentWebhookController(service);

        ResponseEntity<String> response = controller.webhook(
                "stripe", rawRequest("payload", "signature", null), "signature", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForInvalidSignature() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        doThrow(new PaymentProviderException("invalid"))
                .when(service).receive(PaymentProviderType.STRIPE, "payload", "signature");
        PaymentWebhookController controller = new PaymentWebhookController(service);

        ResponseEntity<String> response = controller.webhook(
                "stripe", rawRequest("payload", "signature", null), "signature", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnRetryableStatusForDatabaseFailure() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(service).receive(PaymentProviderType.MOCK, "payload", "signature");
        PaymentWebhookController controller = new PaymentWebhookController(service);

        ResponseEntity<String> response = controller.webhook(
                "mock", rawRequest("payload", null, "signature"), null, "signature");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void shouldReadStripeSignatureHeaderForIntentChannel() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        when(service.receive(PaymentProviderType.STRIPE_INTENT, "payload", "intent-signature"))
                .thenReturn(new PaymentWebhookResult(false, true, "pay-1"));
        PaymentWebhookController controller = new PaymentWebhookController(service);

        ResponseEntity<String> response = controller.webhook(
                "stripe_intent", rawRequest("payload", "intent-signature", null), "intent-signature", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForIntentSignatureFailure() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        doThrow(new PaymentProviderException("invalid"))
                .when(service).receive(PaymentProviderType.STRIPE_INTENT, "payload", "bad");
        PaymentWebhookController controller = new PaymentWebhookController(service);

        ResponseEntity<String> response = controller.webhook(
                "stripe_intent", rawRequest("payload", "bad", null), "bad", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForEmptyBody() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        PaymentWebhookController controller = new PaymentWebhookController(service);

        ResponseEntity<String> response = controller.webhook(
                "stripe_intent", rawRequest("  ", null, null), null, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
    @Test
    void shouldPreserveAlipayFormAndAcknowledgeSuccess() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        String payload = "out_trade_no=pay-1&sign=a%2Bb&trade_status=TRADE_SUCCESS";
        PaymentWebhookController controller = new PaymentWebhookController(service);
        ResponseEntity<String> response = controller.webhook(
                "alipay", rawRequest(payload, null, null), null, null);
        org.mockito.Mockito.verify(service).receive(PaymentProviderType.ALIPAY, payload, null);
        assertEquals("success", response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
