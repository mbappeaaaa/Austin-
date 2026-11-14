package com.java3y.austin.billing.repository;

import com.java3y.austin.billing.domain.QuotaLedgerEntry;
import com.java3y.austin.billing.domain.QuotaLedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuotaLedgerRepository extends JpaRepository<QuotaLedgerEntry, Long> {

    Optional<QuotaLedgerEntry> findByTenantIdAndEntryKey(Long tenantId, String entryKey);

    @Query("select coalesce(sum(-entry.reservedDelta), 0) from QuotaLedgerEntry entry "
            + "where entry.tenantId = :tenantId and entry.entryType = "
            + "com.java3y.austin.billing.domain.QuotaLedgerEntryType.SETTLE "
            + "and entry.created >= :start and entry.created < :end")
    Long sumSettledUsage(@Param("tenantId") Long tenantId,
                         @Param("start") Integer start, @Param("end") Integer end);

    @Query("select coalesce(sum(entry.availableDelta), 0) from QuotaLedgerEntry entry "
            + "where entry.tenantId = :tenantId and entry.entryType = "
            + "com.java3y.austin.billing.domain.QuotaLedgerEntryType.CREDIT "
            + "and entry.businessType = :businessType and entry.created >= :start and entry.created < :end")
    Long sumCredits(@Param("tenantId") Long tenantId,
                    @Param("businessType") String businessType,
                    @Param("start") Integer start, @Param("end") Integer end);

    @Query(value = "SELECT SUBSTRING_INDEX(business_id, ':', 1) AS templateId, "
            + "SUM(-reserved_delta) AS usageAmount FROM billing_quota_ledger "
            + "WHERE tenant_id = :tenantId AND entry_type = 'SETTLE' "
            + "AND created >= :start AND created < :end AND business_type = 'MESSAGE' "
            + "GROUP BY SUBSTRING_INDEX(business_id, ':', 1) ORDER BY usageAmount DESC",
            nativeQuery = true)
    java.util.List<TemplateUsageProjection> summarizeTemplateUsage(
            @Param("tenantId") Long tenantId, @Param("start") Integer start,
            @Param("end") Integer end);

    Page<QuotaLedgerEntry> findAllByTenantIdAndCreatedGreaterThanEqualAndCreatedLessThanOrderByCreatedAscIdAsc(
            Long tenantId, Integer start, Integer end, Pageable pageable);

    Page<QuotaLedgerEntry> findByTenantId(Long tenantId, Pageable pageable);

    Page<QuotaLedgerEntry> findByTenantIdAndEntryType(Long tenantId,
                                                      QuotaLedgerEntryType entryType, Pageable pageable);
}
