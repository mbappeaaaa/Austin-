package com.java3y.austin.handler.receiver.service.impl;

import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.common.enums.MessageType;
import com.java3y.austin.handler.inbox.InboxDispatchService;
import com.java3y.austin.handler.inbox.InboxMessage;
import com.java3y.austin.handler.inbox.InboxRegistrationResult;
import com.java3y.austin.handler.inbox.InboxService;
import com.java3y.austin.support.utils.LogUtils;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import cn.hutool.crypto.digest.DigestUtil;

class ConsumeServiceImplTest {

    @Test
    void shouldPersistInboxBeforeDispatchAndAssignLegacyTenant() {
        InboxService inboxService = mock(InboxService.class);
        InboxDispatchService dispatchService = mock(InboxDispatchService.class);
        InboxMessage inbox = InboxMessage.builder().id(11L).tenantId(AustinConstant.DEFAULT_TENANT_ID)
                .consumerGroup("email.notice").build();
        when(inboxService.register(eq(AustinConstant.DEFAULT_TENANT_ID), eq("email.notice"),
                eq("msg-1"), anyString(), eq(5)))
                .thenReturn(new InboxRegistrationResult(inbox, true));
        ConsumeServiceImpl service = new ConsumeServiceImpl();
        ReflectionTestUtils.setField(service, "inboxService", inboxService);
        ReflectionTestUtils.setField(service, "inboxDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "logUtils", mock(LogUtils.class));
        TaskInfo task = TaskInfo.builder().messageId("msg-1")
                .sendChannel(ChannelType.EMAIL.getCode()).msgType(MessageType.NOTICE.getCode()).build();

        service.consume2Send(Collections.singletonList(task));

        assertEquals(AustinConstant.DEFAULT_TENANT_ID, task.getTenantId());
        InOrder order = inOrder(inboxService, dispatchService);
        order.verify(inboxService).register(eq(AustinConstant.DEFAULT_TENANT_ID),
                eq("email.notice"), eq("msg-1"), anyString(), eq(5));
        order.verify(dispatchService).dispatch(AustinConstant.DEFAULT_TENANT_ID, 11L, "email.notice");
    }

    @Test
    void shouldUseSeparateStableInboxKeyForDeferredDelivery() {
        InboxService inboxService = mock(InboxService.class);
        InboxDispatchService dispatchService = mock(InboxDispatchService.class);
        InboxMessage inbox = InboxMessage.builder().id(12L).tenantId(7L)
                .consumerGroup("email.notice").build();
        String expectedKey = "delivery:" + DigestUtil.sha256Hex("msg-1|night:stable");
        when(inboxService.register(eq(7L), eq("email.notice"), eq(expectedKey),
                anyString(), eq(5))).thenReturn(new InboxRegistrationResult(inbox, true));
        ConsumeServiceImpl service = new ConsumeServiceImpl();
        ReflectionTestUtils.setField(service, "inboxService", inboxService);
        ReflectionTestUtils.setField(service, "inboxDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "logUtils", mock(LogUtils.class));
        TaskInfo task = TaskInfo.builder().tenantId(7L).messageId("msg-1")
                .deliveryAttemptId("night:stable").sendChannel(ChannelType.EMAIL.getCode())
                .msgType(MessageType.NOTICE.getCode()).build();

        service.consume2Send(Collections.singletonList(task));

        verify(inboxService).register(eq(7L), eq("email.notice"), eq(expectedKey),
                anyString(), eq(5));
        verify(dispatchService).dispatch(7L, 12L, "email.notice");
    }
}
