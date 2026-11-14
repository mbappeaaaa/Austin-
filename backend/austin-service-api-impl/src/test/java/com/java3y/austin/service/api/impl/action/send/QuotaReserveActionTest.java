package com.java3y.austin.service.api.impl.action.send;

import com.java3y.austin.billing.domain.QuotaReservation;
import com.java3y.austin.billing.domain.QuotaReservationResult;
import com.java3y.austin.billing.domain.QuotaReservationStatus;
import com.java3y.austin.billing.exception.InsufficientQuotaException;
import com.java3y.austin.billing.service.QuotaService;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.service.api.impl.domain.SendTaskModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaReserveActionTest {

    private QuotaService quotaService;
    private QuotaReserveAction action;

    @BeforeEach
    void setUp() {
        quotaService = mock(QuotaService.class);
        action = new QuotaReserveAction(quotaService);
        ReflectionTestUtils.setField(action, "quotaEnforcementEnabled", true);
    }

    @Test
    void shouldReserveOneQuotaUnitPerValidReceiverBeforeDispatch() {
        TaskInfo task = TaskInfo.builder().tenantId(7L)
                .receiver(new java.util.HashSet<>(Arrays.asList("u1", "u2"))).build();
        QuotaReservation reservation = QuotaReservation.builder().id(21L)
                .status(QuotaReservationStatus.RESERVED).build();
        when(quotaService.reserveWithResult(7L, 2, "request-1:0", "MESSAGE", "10:0"))
                .thenReturn(new QuotaReservationResult(reservation, true));
        ProcessContext<SendTaskModel> context = context(task, "request-1");

        action.process(context);

        assertFalse(context.getNeedBreak());
        assertEquals(21L, task.getQuotaReservationId());
    }

    @Test
    void shouldStopDispatchWhenQuotaIsInsufficient() {
        TaskInfo task = TaskInfo.builder().tenantId(7L)
                .receiver(Collections.singleton("u1")).build();
        when(quotaService.reserveWithResult(7L, 1, "request-2:0", "MESSAGE", "10:0"))
                .thenThrow(new InsufficientQuotaException("insufficient"));
        ProcessContext<SendTaskModel> context = context(task, "request-2");

        action.process(context);

        assertTrue(context.getNeedBreak());
        assertEquals(RespStatusEnum.QUOTA_INSUFFICIENT.getCode(), context.getResponse().getStatus());
    }

    @Test
    void shouldCompensateEarlierReservationWhenLaterTaskCannotReserve() {
        TaskInfo first = TaskInfo.builder().tenantId(7L).receiver(Collections.singleton("u1")).build();
        TaskInfo second = TaskInfo.builder().tenantId(7L).receiver(Collections.singleton("u2")).build();
        QuotaReservation reservation = QuotaReservation.builder().id(21L)
                .status(QuotaReservationStatus.RESERVED).build();
        when(quotaService.reserveWithResult(7L, 1, "request-3:0", "MESSAGE", "10:0"))
                .thenReturn(new QuotaReservationResult(reservation, true));
        when(quotaService.reserveWithResult(7L, 1, "request-3:1", "MESSAGE", "10:1"))
                .thenThrow(new InsufficientQuotaException("insufficient"));
        ProcessContext<SendTaskModel> context = context(Arrays.asList(first, second), "request-3");

        action.process(context);

        assertTrue(context.getNeedBreak());
        verify(quotaService).release(7L, 21L);
    }

    private ProcessContext<SendTaskModel> context(TaskInfo task, String key) {
        return context(Collections.singletonList(task), key);
    }

    private ProcessContext<SendTaskModel> context(java.util.List<TaskInfo> tasks, String key) {
        return ProcessContext.<SendTaskModel>builder().needBreak(false).response(BasicResultVO.success())
                .processModel(SendTaskModel.builder().tenantId(7L).idempotencyKey(key)
                        .messageTemplateId(10L).taskInfo(tasks).build()).build();
    }
}
