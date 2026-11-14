package com.java3y.austin.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
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
@Table(name = "billing_quota_account",
        uniqueConstraints = @UniqueConstraint(name = "uk_billing_quota_account_tenant", columnNames = "tenant_id"))
public class QuotaAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "available_quota", nullable = false)
    private Long availableQuota;
    @Column(name = "reserved_quota", nullable = false)
    private Long reservedQuota;
    @Column(name = "warning_threshold", nullable = false)
    private Long warningThreshold;
    @Column(name = "alert_cycle", nullable = false)
    private Long alertCycle;
    @Column(name = "threshold_alert_active", nullable = false)
    private Boolean thresholdAlertActive;
    @Column(name = "insufficient_alert_active", nullable = false)
    private Boolean insufficientAlertActive;
    private Integer created;
    private Integer updated;
    @Version
    @Column(nullable = false)
    private Long version;
}
