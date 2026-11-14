package com.java3y.austin.auth.security;

/**
 * Austin 后台权限。权限名会作为 Spring Security authority 返回给前端。
 */
public enum AuthPermission {
    USER_MANAGE,
    SYSTEM_ADMIN,
    CHANNEL_READ,
    ACCOUNT_READ,
    ACCOUNT_WRITE,
    TEMPLATE_READ,
    TEMPLATE_WRITE,
    TEMPLATE_EXECUTE,
    TRACE_READ,
    INBOX_ACCESS,
    MATERIAL_WRITE,
    MESSAGE_SEND,
    INTEGRATION_TEST,
    MQ_DEAD_LETTER_MANAGE,
    PAYMENT_READ,
    PAYMENT_PURCHASE,
    QUOTA_READ,
    QUOTA_MANAGE,
    BILLING_OUTBOX_MANAGE,
    REPORT_READ,
    REPORT_GENERATE,
    REPORT_MANAGE
}
