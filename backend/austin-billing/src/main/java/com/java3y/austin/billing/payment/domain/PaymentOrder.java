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
import javax.persistence.Version;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "billing_payment_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_order_no", columnNames = "order_no"),
        @UniqueConstraint(name = "uk_payment_tenant_idempotency",
                columnNames = {"tenant_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_provider_order",
                columnNames = {"provider", "provider_order_id"})
})
public class PaymentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProviderType provider;
    @Column(name = "provider_order_id", length = 128)
    private String providerOrderId;
    @Column(name = "package_code", nullable = false, length = 64)
    private String packageCode;
    @Column(name = "package_name_snapshot", nullable = false, length = 128)
    private String packageNameSnapshot;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;
    @Column(name = "quota_amount", nullable = false)
    private Long quotaAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentOrderStatus status;
    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;
    @Column(name = "paid_at")
    private Integer paidAt;
    @Column(nullable = false)
    private Integer created;
    @Column(nullable = false)
    private Integer updated;
    @Version
    @Column(nullable = false)
    private Long version;
}
