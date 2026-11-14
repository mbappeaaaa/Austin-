package com.java3y.austin.web.exception;

import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.provider.PaymentProviderUnavailableException;
import com.java3y.austin.common.vo.BasicResultVO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionHandlerAdviceTest {
    @Test
    void unexpectedErrorsMustNotExposeInternalDetails() {
        BasicResultVO<String> result = new ExceptionHandlerAdvice().exceptionResponse(new IllegalStateException("internal-secret"));
        assertFalse(result.getMsg().contains("internal-secret"));
        assertFalse(result.getMsg().contains("java."));
        assertNull(result.getData());
    }

    @Test
    void disabledProviderReturnsAnActionableMessage() {
        BasicResultVO<String> result = new ExceptionHandlerAdvice().paymentProviderUnavailable(
                new PaymentProviderUnavailableException(PaymentProviderType.STRIPE_INTENT));
        assertTrue(result.getMsg().contains("支付渠道未启用"));
        assertNull(result.getData());
    }
}
