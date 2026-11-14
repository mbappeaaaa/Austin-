package com.java3y.austin.billing.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "billing_payment_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_event_provider_id",
                columnNames = {"provider", "provider_event_id"}))
public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProviderType provider;
    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;
    @Column(name = "provider_order_id", length = 128)
    private String providerOrderId;
    @Column(name = "order_no", length = 64)
    private String orderNo;
    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 32)
    private PaymentEventStatus eventStatus;
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;
    @Column(name = "provider_created_at")
    private Integer providerCreatedAt;
    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;
    @Column(nullable = false)
    private Integer created;
    @Column(name = "processed_at")
    private Integer processedAt;
}
