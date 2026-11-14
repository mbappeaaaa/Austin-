package com.java3y.austin.billing.payment.repository;

import com.java3y.austin.billing.payment.domain.QuotaPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuotaPackageRepository extends JpaRepository<QuotaPackage, Long> {
    Optional<QuotaPackage> findByPackageCodeAndActiveTrue(String packageCode);
    List<QuotaPackage> findAllByActiveTrueOrderByAmountMinorAsc();
}
