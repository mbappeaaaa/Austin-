package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.billing.outbox.OutboxAdminService;
import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.domain.OutboxStatus;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.vo.OutboxDeadLetterPageVo;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxAdminControllerTest {
    @Test
    void shouldScopeDeadLettersAndReplayToCurrentTenant() {
        OutboxAdminService service = mock(OutboxAdminService.class);
        CurrentUserService currentUser = currentUser(7L, "ADMIN");
        OutboxEvent event = OutboxEvent.builder().id(11L).tenantId(7L)
                .eventType("billing.payment.succeeded.v1").status(OutboxStatus.DEAD)
                .attemptCount(5).replayCount(0).build();
        when(service.listDead(7L, 1, 20))
                .thenReturn(new PageImpl<>(Collections.singletonList(event)));
        when(service.replay(7L, 11L, 60)).thenReturn(true);
        OutboxAdminController controller = new OutboxAdminController(service, currentUser);
        ReflectionTestUtils.setField(controller, "leaseSeconds", 60);

        OutboxDeadLetterPageVo response = controller.deadLetters(1, 20);
        assertEquals(1L, response.getCount());
        assertTrue(controller.replay(11L));
        verify(service).listDead(7L, 1, 20);
        verify(service).replay(7L, 11L, 60);
    }

    @Test
    void shouldRejectNonAdminEvenIfCalledWithoutWebSecurity() {
        OutboxAdminController controller = new OutboxAdminController(
                mock(OutboxAdminService.class), currentUser(7L, "OPERATOR"));
        assertThrows(CommonException.class, () -> controller.deadLetters(1, 20));
    }

    private CurrentUserService currentUser(Long tenantId, String role) {
        CurrentUserService service = mock(CurrentUserService.class);
        when(service.tenantId()).thenReturn(tenantId);
        when(service.isAdmin()).thenReturn("ADMIN".equals(role));
        return service;
    }
}
