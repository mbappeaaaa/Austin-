package com.java3y.austin.auth.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRolePermissionTest {

    @Test
    void adminShouldHaveEveryPermission() {
        assertEquals(AuthPermission.values().length, AuthRole.ADMIN.getPermissions().size());
    }

    @Test
    void ordinaryUserShouldOnlyAccessInbox() {
        assertEquals(1, AuthRole.USER.getPermissions().size());
        assertTrue(AuthRole.USER.getPermissions().contains(AuthPermission.INBOX_ACCESS));
        assertFalse(AuthRole.USER.getPermissions().contains(AuthPermission.USER_MANAGE));
    }

    @Test
    void operatorAndAuditorShouldReadButNotManageQuota() {
        assertTrue(AuthRole.OPERATOR.getPermissions().contains(AuthPermission.QUOTA_READ));
        assertTrue(AuthRole.AUDITOR.getPermissions().contains(AuthPermission.QUOTA_READ));
        assertFalse(AuthRole.OPERATOR.getPermissions().contains(AuthPermission.QUOTA_MANAGE));
        assertFalse(AuthRole.AUDITOR.getPermissions().contains(AuthPermission.QUOTA_MANAGE));
    }
}
