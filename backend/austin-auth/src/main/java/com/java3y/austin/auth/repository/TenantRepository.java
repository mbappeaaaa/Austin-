package com.java3y.austin.auth.repository;

import com.java3y.austin.auth.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByTenantCode(String tenantCode);

    List<Tenant> findAllByStatusOrderByIdAsc(String status);
}
