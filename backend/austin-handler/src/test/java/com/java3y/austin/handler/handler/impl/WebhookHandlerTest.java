package com.java3y.austin.handler.handler.impl;

import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.dto.account.WebhookAccount;
import com.java3y.austin.common.dto.model.WebhookContentModel;
import com.java3y.austin.support.utils.AccountUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookHandlerTest {

    @Test
    void shouldRejectPrivateTargetBeforeSending() {
        AccountUtils accountUtils = mock(AccountUtils.class);
        when(accountUtils.getAccountById(1, WebhookAccount.class)).thenReturn(
                WebhookAccount.builder().webhook("https://127.0.0.1/internal").build());
        WebhookHandler handler = new WebhookHandler(accountUtils);
        TaskInfo task = TaskInfo.builder().messageId("msg-1").sendAccount(1)
                .contentModel(WebhookContentModel.builder().body("{\"ok\":true}").build()).build();

        assertFalse(handler.handler(task));
    }
}
