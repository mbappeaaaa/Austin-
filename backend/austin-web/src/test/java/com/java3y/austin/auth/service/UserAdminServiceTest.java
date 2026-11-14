package com.java3y.austin.auth.service;

import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import com.java3y.austin.auth.security.AuthRole;
import com.java3y.austin.auth.vo.UserAdminVo;
import com.java3y.austin.auth.vo.UserCreateRequest;
import com.java3y.austin.auth.vo.UserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdminServiceTest {

    @Test
    void shouldCreateOrdinaryUserWithEncodedPassword() {
        SysUserRepository repository = mock(SysUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserAdminService service = new UserAdminService(repository, encoder);
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setDisplayName("Alice");
        request.setPassword("password-123");
        request.setRole("USER");

        when(repository.findByUsername("alice")).thenReturn(Optional.empty());
        when(encoder.encode("password-123")).thenReturn("encoded");
        when(repository.save(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });

        UserAdminVo result = service.create(request, 7L);

        assertEquals(AuthRole.USER.name(), result.getRole());
        assertEquals(7L, result.getTenantId());
        assertTrue(result.getEnabled());
        verify(encoder).encode("password-123");
    }

    @Test
    void shouldKeepAtLeastOneEnabledAdministrator() {
        SysUserRepository repository = mock(SysUserRepository.class);
        UserAdminService service = new UserAdminService(repository, mock(PasswordEncoder.class));
        SysUser administrator = SysUser.builder().id(1L).tenantId(7L).username("admin")
                .passwordHash("hash").displayName("Admin").role(AuthRole.ADMIN.name())
                .enabled(true).created(1).updated(1).build();
        UserUpdateRequest request = new UserUpdateRequest();
        request.setDisplayName("Admin");
        request.setRole("USER");
        request.setEnabled(true);
        when(repository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(administrator));
        when(repository.countByTenantIdAndRoleAndEnabledTrue(7L, AuthRole.ADMIN.name())).thenReturn(1L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.update(1L, request, 2L, 7L));

        assertTrue(exception.getReason().contains("至少需要保留一个"));
    }

    @Test
    void shouldRejectSelfDisable() {
        SysUserRepository repository = mock(SysUserRepository.class);
        UserAdminService service = new UserAdminService(repository, mock(PasswordEncoder.class));
        SysUser administrator = SysUser.builder().id(1L).tenantId(7L).username("admin")
                .passwordHash("hash").displayName("Admin").role(AuthRole.ADMIN.name())
                .enabled(true).created(1).updated(1).build();
        UserUpdateRequest request = new UserUpdateRequest();
        request.setDisplayName("Admin");
        request.setRole("ADMIN");
        request.setEnabled(false);
        when(repository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(administrator));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.update(1L, request, 1L, 7L));

        assertTrue(exception.getReason().contains("不能停用自己"));
    }

    @Test
    void shouldHideUsersOwnedByAnotherTenant() {
        SysUserRepository repository = mock(SysUserRepository.class);
        UserAdminService service = new UserAdminService(repository, mock(PasswordEncoder.class));
        when(repository.findByIdAndTenantId(9L, 7L)).thenReturn(Optional.empty());
        UserUpdateRequest request = new UserUpdateRequest();
        request.setDisplayName("Other Tenant User");
        request.setRole("USER");
        request.setEnabled(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.update(9L, request, 1L, 7L));

        assertEquals(404, exception.getStatus().value());
    }
}
