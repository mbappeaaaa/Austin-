package com.java3y.austin.billing.payment.repository;

import com.java3y.austin.billing.payment.domain.PaymentEvent;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByProviderAndProviderEventId(
            PaymentProviderType provider, String providerEventId);

    @Modifying
    @Query(value = "INSERT IGNORE INTO billing_payment_event "
            + "(provider, provider_event_id, provider_order_id, order_no, event_type, event_status, "
            + "payload_hash, provider_created_at, created) "
            + "VALUES (:provider, :providerEventId, :providerOrderId, :orderNo, :eventType, "
            + "'RECEIVED', :payloadHash, :providerCreatedAt, :created)", nativeQuery = true)
    int insertIgnore(@Param("provider") String provider,
                     @Param("providerEventId") String providerEventId,
                     @Param("providerOrderId") String providerOrderId,
                     @Param("orderNo") String orderNo,
                     @Param("eventType") String eventType,
                     @Param("payloadHash") String payloadHash,
                     @Param("providerCreatedAt") Integer providerCreatedAt,
                     @Param("created") Integer created);
}
