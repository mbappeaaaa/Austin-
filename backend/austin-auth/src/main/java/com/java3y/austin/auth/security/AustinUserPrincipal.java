package com.java3y.austin.auth.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@AllArgsConstructor
public class AustinUserPrincipal implements UserDetails {
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final Long tenantId;
    private final String username;
    private final String password;
    private final String displayName;
    private final String role;
    private final boolean enabled;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        try {
            for (AuthPermission permission : AuthRole.parse(role).getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.name()));
            }
        } catch (IllegalArgumentException ignored) {
            // 未知角色仅保留 ROLE_xxx，不授予任何业务权限。
        }
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
