package com.java3y.austin.auth.service;

import com.java3y.austin.auth.security.AustinUserPrincipal;
import com.java3y.austin.auth.security.AuthRole;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public Long userId() {
        return principal().getId();
    }

    public Long tenantId() {
        return principal().getTenantId();
    }

    public String username() {
        return principal().getUsername();
    }

    public String role() {
        return principal().getRole();
    }

    public boolean isAdmin() {
        return AuthRole.ADMIN.name().equals(role());
    }

    private AustinUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("当前请求未登录");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AustinUserPrincipal)) {
            throw new IllegalStateException("当前登录身份无效");
        }
        return (AustinUserPrincipal) principal;
    }
}
