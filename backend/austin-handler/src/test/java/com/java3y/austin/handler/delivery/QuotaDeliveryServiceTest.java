package com.java3y.austin.handler.delivery;

import com.java3y.austin.billing.domain.QuotaCompletionOutcome;
import com.java3y.austin.billing.domain.QuotaReservationStatus;
import com.java3y.austin.billing.service.QuotaService;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.handler.handler.DeliveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaDeliveryServiceTest {

    private QuotaService quotaService;
    private QuotaDeliveryService service;
    private TaskInfo taskInfo;

    @BeforeEach
    void setUp() {
        quotaService = mock(QuotaService.class);
        service = new QuotaDeliveryService(quotaService);
        taskInfo = TaskInfo.builder().tenantId(7L).quotaReservationId(21L)
                .messageId("msg-1").build();
    }

    @Test
    void shouldSettleSuccessfulDelivery() {
        service.complete(taskInfo, 2L, "delivery:abc", DeliveryResult.success());

        verify(quotaService).settlePartially(7L, 21L, 2L, "delivery:abc");
    }

    @Test
    void shouldFreezeUnknownResultAndRequireManualReview() {
        assertThrows(NonRetryableDeliveryException.class,
                () -> service.complete(taskInfo, 1L, "delivery:abc",
                        DeliveryResult.unknown("PROVIDER_RESULT_UNKNOWN")));

        verify(quotaService).markUnknown(7L, 21L);
    }

    @Test
    void shouldSkipProviderWhenLogicalOperationWasCompleted() {
        when(quotaService.findReservationStatus(7L, 21L))
                .thenReturn(Optional.of(QuotaReservationStatus.PARTIALLY_PROCESSED));
        when(quotaService.findCompletionOutcome(7L, 21L, "delivery:abc"))
                .thenReturn(Optional.of(QuotaCompletionOutcome.SETTLED));

        assertTrue(service.isAlreadyCompleted(taskInfo, "delivery:abc"));
    }

    @Test
    void shouldBlockAutomaticReplayWhileReservationIsUnknown() {
        when(quotaService.findReservationStatus(7L, 21L))
                .thenReturn(Optional.of(QuotaReservationStatus.UNKNOWN));

        assertThrows(NonRetryableDeliveryException.class,
                () -> service.isAlreadyCompleted(taskInfo, "delivery:abc"));
    }

    @Test
    void shouldNotCallProviderAfterReservationWasManuallyCompleted() {
        when(quotaService.findReservationStatus(7L, 21L))
                .thenReturn(Optional.of(QuotaReservationStatus.COMPLETED));

        assertTrue(service.isAlreadyCompleted(taskInfo, "delivery:not-recorded"));
    }
}
