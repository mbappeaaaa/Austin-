package com.java3y.austin.web.service;

import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import com.java3y.austin.billing.outbox.OutboxMessage;
import com.java3y.austin.billing.service.QuotaAlertOutboxAppender;
import com.java3y.austin.support.service.InAppMessageService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaAlertNotificationHandlerTest {
    @Test
    void shouldNotifyTenantAdminsForThresholdAndInsufficientEvents() {
        SysUserRepository users = mock(SysUserRepository.class);
        InAppMessageService messages = mock(InAppMessageService.class);
        when(users.findAllByTenantIdAndRoleAndEnabledTrueOrderByIdAsc(7L, "ADMIN"))
                .thenReturn(Collections.singletonList(SysUser.builder().username("admin").build()));

        new QuotaThresholdNotificationHandler(users, messages).handle(thresholdMessage(7L));
        new QuotaInsufficientNotificationHandler(users, messages).handle(insufficientMessage(7L));

        verify(messages).storeSystemMessage(eq(7L), eq(Collections.singletonList("admin")),
                eq("消息额度已达到告警阈值"), contains("告警阈值 10"),
                eq("/billing/quota"), eq("quota-threshold:7:2"));
        verify(messages).storeSystemMessage(eq(7L), eq(Collections.singletonList("admin")),
                eq("消息额度不足，发送已阻止"), contains("发送已被阻止"),
                eq("/billing/quota"), eq("quota-insufficient:7:2"));
    }

    @Test
    void shouldRejectEnvelopeMismatch() {
        SysUserRepository users = mock(SysUserRepository.class);
        InAppMessageService messages = mock(InAppMessageService.class);
        assertThrows(IllegalArgumentException.class,
                () -> new QuotaThresholdNotificationHandler(users, messages)
                        .handle(thresholdMessage(8L)));
        assertThrows(IllegalArgumentException.class,
                () -> new QuotaInsufficientNotificationHandler(users, messages)
                        .handle(insufficientMessage(8L)));
    }

    private OutboxMessage thresholdMessage(Long envelopeTenantId) {
        return OutboxMessage.builder().id(1L).tenantId(envelopeTenantId)
                .aggregateType("QUOTA_ACCOUNT").aggregateId("11")
                .eventType(QuotaAlertOutboxAppender.THRESHOLD_REACHED_EVENT)
                .eventKey("quota-threshold:7:2")
                .payload("{\"tenantId\":7,\"accountId\":11,\"alertCycle\":2,"
                        + "\"availableQuota\":9,\"reservedQuota\":1,\"warningThreshold\":10}")
                .build();
    }

    private OutboxMessage insufficientMessage(Long envelopeTenantId) {
        return OutboxMessage.builder().id(2L).tenantId(envelopeTenantId)
                .aggregateType("QUOTA_ACCOUNT").aggregateId("11")
                .eventType(QuotaAlertOutboxAppender.INSUFFICIENT_EVENT)
                .eventKey("quota-insufficient:7:2")
                .payload("{\"tenantId\":7,\"accountId\":11,\"alertCycle\":2,"
                        + "\"availableQuota\":2,\"reservedQuota\":1,\"requestedAmount\":5}")
                .build();
    }
}
