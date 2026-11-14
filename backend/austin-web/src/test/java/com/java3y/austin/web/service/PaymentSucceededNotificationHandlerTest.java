package com.java3y.austin.web.service;

import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import com.java3y.austin.billing.outbox.OutboxMessage;
import com.java3y.austin.billing.payment.service.PaymentWebhookProcessor;
import com.java3y.austin.support.service.InAppMessageService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSucceededNotificationHandlerTest {
    @Test
    void shouldNotifyOnlyEnabledAdminsInEventTenant() {
        SysUserRepository users = mock(SysUserRepository.class);
        InAppMessageService messages = mock(InAppMessageService.class);
        when(users.findAllByTenantIdAndRoleAndEnabledTrueOrderByIdAsc(7L, "ADMIN"))
                .thenReturn(Arrays.asList(user("admin-a"), user("admin-b")));
        PaymentSucceededNotificationHandler handler =
                new PaymentSucceededNotificationHandler(users, messages);

        handler.handle(message(7L, "pay_1"));

        verify(messages).storeSystemMessage(eq(7L), eq(Arrays.asList("admin-a", "admin-b")),
                eq("消息额度购买成功"), contains("USD 1.00"),
                eq("/billing/payments/pay_1"), eq("payment-succeeded:pay_1"));
    }

    @Test
    void shouldRejectTenantMismatchAndMissingAdmin() {
        SysUserRepository users = mock(SysUserRepository.class);
        InAppMessageService messages = mock(InAppMessageService.class);
        PaymentSucceededNotificationHandler handler =
                new PaymentSucceededNotificationHandler(users, messages);

        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(message(8L, "pay_1")));

        when(users.findAllByTenantIdAndRoleAndEnabledTrueOrderByIdAsc(7L, "ADMIN"))
                .thenReturn(Collections.emptyList());
        assertThrows(IllegalStateException.class,
                () -> handler.handle(message(7L, "pay_1")));
    }

    private OutboxMessage message(Long envelopeTenantId, String orderNo) {
        String payload = "{\"tenantId\":7,\"orderNo\":\"pay_1\",\"amountMinor\":100,"
                + "\"currency\":\"USD\",\"quotaAmount\":1000}";
        return OutboxMessage.builder().id(11L).tenantId(envelopeTenantId)
                .aggregateType("PAYMENT_ORDER").aggregateId(orderNo)
                .eventType(PaymentWebhookProcessor.PAYMENT_SUCCEEDED_EVENT)
                .eventKey("payment-succeeded:pay_1").payload(payload).build();
    }

    private SysUser user(String username) {
        return SysUser.builder().username(username).build();
    }
}
