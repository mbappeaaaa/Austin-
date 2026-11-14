package com.java3y.austin.support.service;

import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.dto.model.ImContentModel;
import com.java3y.austin.support.dao.InAppMessageDao;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InAppMessageServiceTest {

    @Test
    void shouldStoreOneMessagePerReceiverAndKeepUnread() {
        InAppMessageDao dao = mock(InAppMessageDao.class);
        when(dao.insertIgnore(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        InAppMessageService service = new InAppMessageService(dao);
        TaskInfo task = TaskInfo.builder().tenantId(7L).messageId("msg-1")
                .messageTemplateId(1L).businessId(2L)
                .receiver(new HashSet<>(Arrays.asList("u1", "u2")))
                .contentModel(ImContentModel.builder().title("title").content("content").build()).build();

        assertEquals(2, service.store(task));

        verify(dao, times(2)).insertIgnore(eq(7L), any(), eq("title"), eq("content"),
                any(), eq(1L), eq(2L), eq("msg-1"), any());
    }

    @Test
    void shouldUseReceiverScopedBatchUpdates() {
        InAppMessageDao dao = mock(InAppMessageDao.class);
        when(dao.markReadBatch(any(), any(), any(), any())).thenReturn(2);
        when(dao.logicalDeleteBatch(any(), any(), any(), any())).thenReturn(2);
        InAppMessageService service = new InAppMessageService(dao);

        assertEquals(2, service.markReadBatch(7L, "u1", Arrays.asList(1L, 2L)));
        assertEquals(2, service.logicalDeleteBatch(7L, "u1", Arrays.asList(1L, 2L)));
    }

    @Test
    void shouldAssignLegacyTenantToMessageQueuedBeforeUpgrade() {
        InAppMessageDao dao = mock(InAppMessageDao.class);
        when(dao.insertIgnore(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        InAppMessageService service = new InAppMessageService(dao);
        TaskInfo task = TaskInfo.builder().messageId("legacy-msg")
                .receiver(new HashSet<>(Arrays.asList("u1")))
                .contentModel(ImContentModel.builder().content("content").build()).build();

        assertEquals(1, service.store(task));

        verify(dao).insertIgnore(eq(1L), eq("u1"), eq(""), eq("content"),
                any(), any(), any(), eq("legacy-msg"), any());
    }

    @Test
    void shouldTreatDuplicateSystemMessageAsSuccessfulNoOp() {
        InAppMessageDao dao = mock(InAppMessageDao.class);
        when(dao.insertIgnore(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        InAppMessageService service = new InAppMessageService(dao);

        assertEquals(0, service.storeSystemMessage(7L, Arrays.asList("admin"),
                "title", "content", "/orders/1", "payment-succeeded:1"));
    }
}
