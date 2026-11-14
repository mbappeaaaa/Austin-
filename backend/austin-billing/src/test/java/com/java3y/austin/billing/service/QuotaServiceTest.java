package com.java3y.austin.billing.service;

import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.domain.QuotaLedgerEntry;
import com.java3y.austin.billing.domain.QuotaLedgerEntryType;
import com.java3y.austin.billing.domain.QuotaReservation;
import com.java3y.austin.billing.domain.QuotaReservationStatus;
import com.java3y.austin.billing.exception.InsufficientQuotaException;
import com.java3y.austin.billing.exception.QuotaOperationConflictException;
import com.java3y.austin.billing.repository.QuotaAccountRepository;
import com.java3y.austin.billing.repository.QuotaLedgerRepository;
import com.java3y.austin.billing.repository.QuotaReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaServiceTest {

    private static final Long TENANT_ID = 7L;
    private QuotaAccountRepository accountRepository;
    private QuotaReservationRepository reservationRepository;
    private QuotaLedgerRepository ledgerRepository;
    private QuotaAlertOutboxAppender alertOutboxAppender;
    private QuotaService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(QuotaAccountRepository.class);
        reservationRepository = mock(QuotaReservationRepository.class);
        ledgerRepository = mock(QuotaLedgerRepository.class);
        alertOutboxAppender = mock(QuotaAlertOutboxAppender.class);
        service = new QuotaService(accountRepository, reservationRepository,
                ledgerRepository, alertOutboxAppender);
    }

    @Test
    void shouldReserveQuotaAndAppendImmutableLedgerEntry() {
        QuotaAccount account = account(10, 0);
        when(accountRepository.findWithLockByTenantId(TENANT_ID)).thenReturn(Optional.of(account));
        when(reservationRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, "send-1"))
                .thenReturn(Optional.empty());
        when(reservationRepository.saveAndFlush(any(QuotaReservation.class))).thenAnswer(invocation -> {
            QuotaReservation reservation = invocation.getArgument(0);
            reservation.setId(21L);
            return reservation;
        });

        QuotaReservation reservation = service.reserve(TENANT_ID, 3, "send-1", "MESSAGE", "msg-1");

        assertEquals(QuotaReservationStatus.RESERVED, reservation.getStatus());
        assertEquals(7L, account.getAvailableQuota());
        assertEquals(3L, account.getReservedQuota());
        ArgumentCaptor<QuotaLedgerEntry> ledger = ArgumentCaptor.forClass(QuotaLedgerEntry.class);
        verify(ledgerRepository).save(ledger.capture());
        assertEquals(QuotaLedgerEntryType.RESERVE, ledger.getValue().getEntryType());
        assertEquals(-3L, ledger.getValue().getAvailableDelta());
        assertEquals(3L, ledger.getValue().getReservedDelta());
        assertEquals(21L, ledger.getValue().getReservationId());
    }

    @Test
    void shouldRejectReservationWhenAvailableQuotaIsInsufficient() {
        when(accountRepository.findWithLockByTenantId(TENANT_ID))
                .thenReturn(Optional.of(account(2, 0)));
        when(reservationRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, "send-2"))
                .thenReturn(Optional.empty());

        assertThrows(InsufficientQuotaException.class,
                () -> service.reserve(TENANT_ID, 3, "send-2", "MESSAGE", "msg-2"));

        verify(reservationRepository, never()).saveAndFlush(any());
        verify(ledgerRepository, never()).save(any());
        verify(alertOutboxAppender).appendInsufficient(any(QuotaAccount.class),
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("MESSAGE"),
                org.mockito.ArgumentMatchers.eq("msg-2"));
    }

    @Test
    void shouldReturnSameReservationForIdempotentRetryAndRejectChangedPayload() {
        QuotaReservation existing = QuotaReservation.builder().id(21L).tenantId(TENANT_ID)
                .idempotencyKey("send-1").amount(3L).businessType("MESSAGE").businessId("msg-1")
                .status(QuotaReservationStatus.RESERVED).build();
        when(accountRepository.findWithLockByTenantId(TENANT_ID))
                .thenReturn(Optional.of(account(7, 3)));
        when(reservationRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, "send-1"))
                .thenReturn(Optional.of(existing));

        assertEquals(existing, service.reserve(TENANT_ID, 3, "send-1", "MESSAGE", "msg-1"));
        assertThrows(QuotaOperationConflictException.class,
                () -> service.reserve(TENANT_ID, 4, "send-1", "MESSAGE", "msg-1"));

        verify(reservationRepository, never()).saveAndFlush(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void shouldReleaseReservedQuotaExactlyOnce() {
        QuotaAccount account = account(7, 3);
        QuotaReservation reservation = QuotaReservation.builder().id(21L).tenantId(TENANT_ID)
                .amount(3L).businessType("MESSAGE").businessId("msg-1")
                .status(QuotaReservationStatus.RESERVED).build();
        when(accountRepository.findWithLockByTenantId(TENANT_ID)).thenReturn(Optional.of(account));
        when(reservationRepository.findByIdAndTenantId(21L, TENANT_ID))
                .thenReturn(Optional.of(reservation));

        assertEquals(QuotaReservationStatus.RELEASED, service.release(TENANT_ID, 21L).getStatus());
        assertEquals(10L, account.getAvailableQuota());
        assertEquals(0L, account.getReservedQuota());

        service.release(TENANT_ID, 21L);
        assertEquals(10L, account.getAvailableQuota());
        verify(ledgerRepository).save(any(QuotaLedgerEntry.class));
    }

    @Test
    void shouldPartiallySettleThenReleaseRemainingExactlyOnce() {
        QuotaAccount account = account(7, 3);
        QuotaReservation reservation = QuotaReservation.builder().id(21L).tenantId(TENANT_ID)
                .amount(3L).settledAmount(0L).releasedAmount(0L)
                .businessType("MESSAGE").businessId("msg-1")
                .status(QuotaReservationStatus.RESERVED).build();
        String settleKey = "settle:21:receiver-a";
        QuotaLedgerEntry settledLedger = QuotaLedgerEntry.builder().tenantId(TENANT_ID)
                .reservationId(21L).entryType(QuotaLedgerEntryType.SETTLE).entryKey(settleKey)
                .availableDelta(0L).reservedDelta(-1L).build();
        when(accountRepository.findWithLockByTenantId(TENANT_ID)).thenReturn(Optional.of(account));
        when(reservationRepository.findByIdAndTenantId(21L, TENANT_ID))
                .thenReturn(Optional.of(reservation));
        when(ledgerRepository.findByTenantIdAndEntryKey(TENANT_ID, settleKey))
                .thenReturn(Optional.empty(), Optional.of(settledLedger));
        when(ledgerRepository.findByTenantIdAndEntryKey(TENANT_ID, "release:21:filtered"))
                .thenReturn(Optional.empty());

        service.settlePartially(TENANT_ID, 21L, 1, "receiver-a");
        assertEquals(QuotaReservationStatus.PARTIALLY_PROCESSED, reservation.getStatus());
        assertEquals(1L, reservation.getSettledAmount());
        assertEquals(2L, account.getReservedQuota());

        service.settlePartially(TENANT_ID, 21L, 1, "receiver-a");
        service.releaseRemaining(TENANT_ID, 21L, "filtered");

        assertEquals(QuotaReservationStatus.COMPLETED, reservation.getStatus());
        assertEquals(1L, reservation.getSettledAmount());
        assertEquals(2L, reservation.getReleasedAmount());
        assertEquals(9L, account.getAvailableQuota());
        assertEquals(0L, account.getReservedQuota());
        ArgumentCaptor<QuotaLedgerEntry> ledgers = ArgumentCaptor.forClass(QuotaLedgerEntry.class);
        verify(ledgerRepository, org.mockito.Mockito.times(2)).save(ledgers.capture());
        assertEquals(Arrays.asList(QuotaLedgerEntryType.SETTLE, QuotaLedgerEntryType.RELEASE),
                ledgers.getAllValues().stream().map(QuotaLedgerEntry::getEntryType)
                        .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void shouldRejectOppositeOutcomeForSameLogicalOperation() {
        QuotaAccount account = account(7, 3);
        QuotaReservation reservation = QuotaReservation.builder().id(21L).tenantId(TENANT_ID)
                .amount(3L).settledAmount(0L).releasedAmount(0L)
                .businessType("MESSAGE").businessId("msg-1")
                .status(QuotaReservationStatus.RESERVED).build();
        when(accountRepository.findWithLockByTenantId(TENANT_ID)).thenReturn(Optional.of(account));
        when(reservationRepository.findByIdAndTenantId(21L, TENANT_ID))
                .thenReturn(Optional.of(reservation));
        when(ledgerRepository.findByTenantIdAndEntryKey(TENANT_ID, "settle:21:receiver-a"))
                .thenReturn(Optional.empty());
        when(ledgerRepository.findByTenantIdAndEntryKey(TENANT_ID, "release:21:receiver-a"))
                .thenReturn(Optional.of(QuotaLedgerEntry.builder().entryType(QuotaLedgerEntryType.RELEASE)
                        .reservationId(21L).availableDelta(1L).reservedDelta(-1L).build()));

        assertThrows(QuotaOperationConflictException.class,
                () -> service.settlePartially(TENANT_ID, 21L, 1, "receiver-a"));

        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void shouldAlertOncePerLowQuotaCycleAndResetAfterCredit() {
        QuotaAccount account = account(3, 0);
        when(accountRepository.findWithLockByTenantId(TENANT_ID)).thenReturn(Optional.of(account));
        when(reservationRepository.findByTenantIdAndIdempotencyKey(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), any(String.class)))
                .thenReturn(Optional.empty());
        when(reservationRepository.saveAndFlush(any(QuotaReservation.class))).thenAnswer(invocation -> {
            QuotaReservation reservation = invocation.getArgument(0);
            reservation.setId(20L + reservation.getAmount());
            return reservation;
        });

        service.reserve(TENANT_ID, 1, "send-low-1", "MESSAGE", "msg-low-1");
        service.reserve(TENANT_ID, 1, "send-low-2", "MESSAGE", "msg-low-2");

        verify(alertOutboxAppender, times(1)).appendThresholdReached(any(QuotaAccount.class),
                org.mockito.ArgumentMatchers.eq("RESERVE"),
                org.mockito.ArgumentMatchers.eq("MESSAGE"), any(String.class));
        assertEquals(true, account.getThresholdAlertActive());

        account.setInsufficientAlertActive(true);
        when(ledgerRepository.findByTenantIdAndEntryKey(TENANT_ID, "top-up"))
                .thenReturn(Optional.empty());
        service.credit(TENANT_ID, 10, "top-up", "PAYMENT", "pay-1");

        assertEquals(1L, account.getAlertCycle());
        assertEquals(false, account.getThresholdAlertActive());
        assertEquals(false, account.getInsufficientAlertActive());
    }

    private QuotaAccount account(long available, long reserved) {
        return QuotaAccount.builder().id(1L).tenantId(TENANT_ID).availableQuota(available)
                .reservedQuota(reserved).warningThreshold(2L).alertCycle(0L)
                .thresholdAlertActive(false).insufficientAlertActive(false).build();
    }
}
