package com.java3y.austin.handler.inbox;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboxAdminServiceTest {

    @Test
    void shouldListOnlyCurrentTenantDeadLetters() {
        InboxMessageRepository repository = mock(InboxMessageRepository.class);
        InboxMessage dead = InboxMessage.builder().id(11L).tenantId(7L).status(InboxStatus.DEAD).build();
        when(repository.findByTenantIdAndStatusOrderByUpdatedDescIdDesc(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(InboxStatus.DEAD),
                any(Pageable.class))).thenReturn(new PageImpl<>(Collections.singletonList(dead)));
        InboxAdminService service = new InboxAdminService(
                repository, mock(InboxService.class), mock(InboxDispatchService.class));

        assertEquals(1, service.listDead(7L, 1, 20).getTotalElements());

        verify(repository).findByTenantIdAndStatusOrderByUpdatedDescIdDesc(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(InboxStatus.DEAD),
                any(Pageable.class));
    }

    @Test
    void shouldReplayWithinTenantThenDispatch() {
        InboxMessageRepository repository = mock(InboxMessageRepository.class);
        InboxService inboxService = mock(InboxService.class);
        InboxDispatchService dispatchService = mock(InboxDispatchService.class);
        InboxMessage message = InboxMessage.builder().id(11L).tenantId(7L)
                .consumerGroup("email.notice").status(InboxStatus.PENDING).build();
        when(inboxService.replayDead(7L, 11L)).thenReturn(message);
        when(dispatchService.dispatch(7L, 11L, "email.notice")).thenReturn(true);
        InboxAdminService service = new InboxAdminService(repository, inboxService, dispatchService);

        assertTrue(service.replay(7L, 11L));

        verify(inboxService).replayDead(7L, 11L);
        verify(dispatchService).dispatch(7L, 11L, "email.notice");
    }
}
