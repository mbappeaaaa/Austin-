package com.java3y.austin.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
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

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "billing_quota_ledger",
        uniqueConstraints = @UniqueConstraint(name = "uk_quota_ledger_tenant_key",
                columnNames = {"tenant_id", "entry_key"}))
public class QuotaLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(name = "reservation_id")
    private Long reservationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private QuotaLedgerEntryType entryType;
    @Column(name = "entry_key", nullable = false, length = 160)
    private String entryKey;
    @Column(name = "available_delta", nullable = false)
    private Long availableDelta;
    @Column(name = "reserved_delta", nullable = false)
    private Long reservedDelta;
    @Column(name = "available_after", nullable = false)
    private Long availableAfter;
    @Column(name = "reserved_after", nullable = false)
    private Long reservedAfter;
    @Column(name = "business_type", nullable = false, length = 32)
    private String businessType;
    @Column(name = "business_id", nullable = false, length = 128)
    private String businessId;
    private Integer created;
}
