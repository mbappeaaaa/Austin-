package com.java3y.austin.auth.repository;

import com.java3y.austin.auth.domain.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByIdAndTenantId(Long id, Long tenantId);

    List<SysUser> findAllByTenantId(Long tenantId, Sort sort);

    List<SysUser> findAllByTenantIdAndRoleAndEnabledTrueOrderByIdAsc(Long tenantId, String role);

    long countByTenantIdAndRoleAndEnabledTrue(Long tenantId, String role);
}
