package com.java3y.austin.billing.payment.repository;

import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    @Query("select (count(payment) > 0) from PaymentOrder payment where payment.provider = :provider "
            + "and payment.status in :statuses and (payment.providerOrderId is not null or payment.checkoutUrl is not null)")
    boolean existsByProviderAndStatusIn(@Param("provider") PaymentProviderType provider,
            @Param("statuses") List<com.java3y.austin.billing.payment.domain.PaymentOrderStatus> statuses);
    Optional<PaymentOrder> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);
    Optional<PaymentOrder> findByTenantIdAndOrderNo(Long tenantId, String orderNo);
    Optional<PaymentOrder> findByOrderNo(String orderNo);

    Page<PaymentOrder> findAllByTenantIdOrderByCreatedDescIdDesc(
            Long tenantId, Pageable pageable);

    Page<PaymentOrder> findAllByTenantIdAndStatusOrderByCreatedDescIdDesc(
            Long tenantId, com.java3y.austin.billing.payment.domain.PaymentOrderStatus status,
            Pageable pageable);

    Page<PaymentOrder> findAllByTenantIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAscIdAsc(
            Long tenantId, com.java3y.austin.billing.payment.domain.PaymentOrderStatus status,
            Integer start, Integer end, Pageable pageable);

    @Query(value = "SELECT currency AS currency, SUM(amount_minor) AS amountMinor, "
            + "COUNT(*) AS orderCount FROM billing_payment_order "
            + "WHERE tenant_id = :tenantId AND status = 'SUCCEEDED' "
            + "AND paid_at >= :start AND paid_at < :end GROUP BY currency ORDER BY currency",
            nativeQuery = true)
    List<CurrencyFeeProjection> summarizeSucceededFees(
            @Param("tenantId") Long tenantId, @Param("start") Integer start,
            @Param("end") Integer end);

    @Modifying
    @Query(value = "INSERT IGNORE INTO billing_payment_order "
            + "(tenant_id, order_no, idempotency_key, provider, package_code, package_name_snapshot, "
            + "currency, amount_minor, quota_amount, status, created, updated, version) "
            + "VALUES (:tenantId, :orderNo, :idempotencyKey, :provider, :packageCode, :packageName, "
            + ":currency, :amountMinor, :quotaAmount, 'CREATED', :created, :created, 0)",
            nativeQuery = true)
    int insertIgnore(@Param("tenantId") Long tenantId,
                     @Param("orderNo") String orderNo,
                     @Param("idempotencyKey") String idempotencyKey,
                     @Param("provider") String provider,
                     @Param("packageCode") String packageCode,
                     @Param("packageName") String packageName,
                     @Param("currency") String currency,
                     @Param("amountMinor") Long amountMinor,
                     @Param("quotaAmount") Long quotaAmount,
                     @Param("created") Integer created);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentOrder payment where payment.orderNo = :orderNo")
    Optional<PaymentOrder> findLockedByOrderNo(@Param("orderNo") String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentOrder payment where payment.provider = :provider "
            + "and payment.providerOrderId = :providerOrderId")
    Optional<PaymentOrder> findLockedByProviderAndProviderOrderId(
            @Param("provider") PaymentProviderType provider,
            @Param("providerOrderId") String providerOrderId);
}
