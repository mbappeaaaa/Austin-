package com.java3y.austin.auth.vo;

import com.java3y.austin.auth.security.AustinUserPrincipal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserVo {
    private Long id;
    private Long tenantId;
    private String username;
    private String displayName;
    private String role;
    private List<String> permissions;

    public static AuthUserVo from(AustinUserPrincipal principal) {
        return AuthUserVo.builder().id(principal.getId()).tenantId(principal.getTenantId())
                .username(principal.getUsername())
                .displayName(principal.getDisplayName()).role(principal.getRole())
                .permissions(principal.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> !authority.startsWith("ROLE_"))
                        .sorted().collect(Collectors.toList()))
                .build();
    }
}
