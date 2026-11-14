package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.handler.inbox.InboxAdminService;
import com.java3y.austin.handler.inbox.InboxMessage;
import com.java3y.austin.handler.inbox.InboxStatus;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.vo.InboxDeadLetterPageVo;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboxAdminControllerTest {

    @Test
    void shouldListCurrentTenantDeadLettersWithoutPayload() {
        InboxAdminService adminService = mock(InboxAdminService.class);
        CurrentUserService currentUser = admin(7L);
        InboxMessage message = InboxMessage.builder().id(11L).tenantId(7L)
                .consumerGroup("email.notice").consumeKey("msg-1").payload("sensitive-payload")
                .status(InboxStatus.DEAD).attemptCount(3).maxAttempts(3).build();
        when(adminService.listDead(7L, 1, 20))
                .thenReturn(new PageImpl<>(Collections.singletonList(message)));
        InboxAdminController controller = new InboxAdminController(adminService, currentUser);

        InboxDeadLetterPageVo response = controller.deadLetters(1, 20);

        assertEquals(1, response.getCount());
        assertEquals("msg-1", response.getRows().get(0).getConsumeKey());
        assertFalse(Arrays.stream(response.getRows().get(0).getClass().getDeclaredFields())
                .anyMatch(field -> "payload".equals(field.getName())));
        verify(adminService).listDead(7L, 1, 20);
    }

    @Test
    void shouldReplayOnlyWithinCurrentTenant() {
        InboxAdminService adminService = mock(InboxAdminService.class);
        when(adminService.replay(7L, 11L)).thenReturn(true);
        InboxAdminController controller = new InboxAdminController(adminService, admin(7L));

        assertTrue(controller.replay(11L));

        verify(adminService).replay(7L, 11L);
    }

    @Test
    void shouldRejectNonAdminEvenWhenControllerIsInvokedDirectly() {
        InboxAdminService adminService = mock(InboxAdminService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.isAdmin()).thenReturn(false);
        InboxAdminController controller = new InboxAdminController(adminService, currentUser);

        assertThrows(CommonException.class, () -> controller.replay(11L));

        verify(adminService, never()).replay(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    private CurrentUserService admin(Long tenantId) {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.isAdmin()).thenReturn(true);
        when(currentUser.tenantId()).thenReturn(tenantId);
        return currentUser;
    }
}
