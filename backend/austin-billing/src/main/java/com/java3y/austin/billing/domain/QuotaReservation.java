package com.java3y.austin.billing.domain;

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
import javax.persistence.Version;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "billing_quota_reservation",
        uniqueConstraints = @UniqueConstraint(name = "uk_quota_reservation_tenant_key",
                columnNames = {"tenant_id", "idempotency_key"}))
public class QuotaReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "business_type", nullable = false, length = 32)
    private String businessType;
    @Column(name = "business_id", nullable = false, length = 128)
    private String businessId;
    @Column(nullable = false)
    private Long amount;
    @Column(name = "settled_amount", nullable = false)
    private Long settledAmount;
    @Column(name = "released_amount", nullable = false)
    private Long releasedAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuotaReservationStatus status;
    private Integer created;
    private Integer updated;
    @Version
    @Column(nullable = false)
    private Long version;
}
