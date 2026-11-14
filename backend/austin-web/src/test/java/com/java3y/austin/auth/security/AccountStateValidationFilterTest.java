package com.java3y.austin.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountStateValidationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldInvalidateSessionWhenRoleChanges() throws Exception {
        SysUserRepository repository = mock(SysUserRepository.class);
        AccountStateValidationFilter filter = new AccountStateValidationFilter(repository, new ObjectMapper());
        AustinUserPrincipal principal = new AustinUserPrincipal(1L, 7L, "alice", "hash",
                "Alice", AuthRole.OPERATOR.name(), true);
        SysUser stored = SysUser.builder().id(1L).tenantId(7L).username("alice").passwordHash("hash")
                .displayName("Alice").role(AuthRole.AUDITOR.name()).enabled(true)
                .created(1).updated(2).build();
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(response.getContentAsString().contains("请重新登录"));
    }
}
