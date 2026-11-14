package com.java3y.austin.billing.payment.provider;

import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import com.java3y.austin.billing.payment.domain.VerifiedPaymentStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VerifiedPaymentEvent {
    PaymentProviderType provider;
    String providerEventId;
    String providerOrderId;
    String orderNo;
    String eventType;
    VerifiedPaymentStatus status;
    Long amountMinor;
    String currency;
    Integer providerCreatedAt;
    boolean liveMode;
    String payloadHash;
}
