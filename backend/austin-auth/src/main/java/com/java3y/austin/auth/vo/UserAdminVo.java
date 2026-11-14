package com.java3y.austin.auth.vo;

import com.java3y.austin.auth.domain.SysUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminVo {
    private Long id;
    private Long tenantId;
    private String username;
    private String displayName;
    private String role;
    private Boolean enabled;
    private Integer created;
    private Integer updated;

    public static UserAdminVo from(SysUser user) {
        return UserAdminVo.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .created(user.getCreated())
                .updated(user.getUpdated())
                .build();
    }
}
