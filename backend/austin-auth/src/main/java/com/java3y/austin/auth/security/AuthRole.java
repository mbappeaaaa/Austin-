package com.java3y.austin.auth.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 系统角色及其权限集合。角色只在服务端映射，客户端不能自行提交权限。
 */
public enum AuthRole {
    ADMIN(EnumSet.allOf(AuthPermission.class)),
    OPERATOR(EnumSet.of(
            AuthPermission.CHANNEL_READ,
            AuthPermission.ACCOUNT_READ,
            AuthPermission.ACCOUNT_WRITE,
            AuthPermission.TEMPLATE_READ,
            AuthPermission.TEMPLATE_WRITE,
            AuthPermission.TEMPLATE_EXECUTE,
            AuthPermission.TRACE_READ,
            AuthPermission.INBOX_ACCESS,
            AuthPermission.MATERIAL_WRITE,
            AuthPermission.PAYMENT_READ,
            AuthPermission.QUOTA_READ,
            AuthPermission.REPORT_READ)),
    AUDITOR(EnumSet.of(
            AuthPermission.CHANNEL_READ,
            AuthPermission.TEMPLATE_READ,
            AuthPermission.TRACE_READ,
            AuthPermission.INBOX_ACCESS,
            AuthPermission.PAYMENT_READ,
            AuthPermission.QUOTA_READ,
            AuthPermission.REPORT_READ)),
    USER(EnumSet.of(AuthPermission.INBOX_ACCESS));

    private final Set<AuthPermission> permissions;

    AuthRole(Set<AuthPermission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<AuthPermission> getPermissions() {
        return permissions;
    }

    public static AuthRole parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的角色: " + value);
        }
    }
}
