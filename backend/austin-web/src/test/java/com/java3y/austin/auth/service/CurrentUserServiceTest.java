package com.java3y.austin.auth.service;

import com.java3y.austin.auth.security.AustinUserPrincipal;
import com.java3y.austin.auth.security.AuthRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentUserServiceTest {

    private final CurrentUserService service = new CurrentUserService();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExposeTenantAndUserFromAuthenticatedPrincipal() {
        AustinUserPrincipal principal = new AustinUserPrincipal(
                11L, 7L, "alice", "hash", "Alice", AuthRole.USER.name(), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertEquals(11L, service.userId());
        assertEquals(7L, service.tenantId());
        assertEquals("alice", service.username());
    }

    @Test
    void shouldRejectMissingAuthentication() {
        assertThrows(IllegalStateException.class, service::tenantId);
    }
}
