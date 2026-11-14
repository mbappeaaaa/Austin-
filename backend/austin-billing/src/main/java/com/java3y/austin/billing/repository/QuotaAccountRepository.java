package com.java3y.austin.billing.repository;

import com.java3y.austin.billing.domain.QuotaAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface QuotaAccountRepository extends JpaRepository<QuotaAccount, Long> {

    Optional<QuotaAccount> findByTenantId(Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from QuotaAccount account where account.tenantId = :tenantId")
    Optional<QuotaAccount> findWithLockByTenantId(@Param("tenantId") Long tenantId);
}
