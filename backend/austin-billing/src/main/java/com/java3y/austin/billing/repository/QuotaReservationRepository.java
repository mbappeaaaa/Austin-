package com.java3y.austin.billing.repository;

import com.java3y.austin.billing.domain.QuotaReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuotaReservationRepository extends JpaRepository<QuotaReservation, Long> {

    Optional<QuotaReservation> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    Optional<QuotaReservation> findByIdAndTenantId(Long id, Long tenantId);
}
