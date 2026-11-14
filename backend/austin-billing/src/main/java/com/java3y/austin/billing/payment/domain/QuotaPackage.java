package com.java3y.austin.billing.payment.domain;

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
import javax.persistence.Version;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "billing_quota_package")
public class QuotaPackage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "package_code", nullable = false, length = 64, unique = true)
    private String packageCode;
    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "CNY";
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;
    @Column(name = "quota_amount", nullable = false)
    private Long quotaAmount;
    @Column(name = "sandbox_only", nullable = false)
    private Boolean sandboxOnly;
    @Column(nullable = false)
    private Boolean active;
    @Column(nullable = false)
    private Integer created;
    @Column(nullable = false)
    private Integer updated;
    @Version
    @Column(nullable = false)
    private Long version;
}
