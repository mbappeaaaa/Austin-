package com.java3y.austin.billing.service;

import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.domain.QuotaCompletionOutcome;
import com.java3y.austin.billing.domain.QuotaLedgerEntry;
import com.java3y.austin.billing.domain.QuotaLedgerEntryType;
import com.java3y.austin.billing.domain.QuotaReservation;
import com.java3y.austin.billing.domain.QuotaReservationResult;
import com.java3y.austin.billing.domain.QuotaReservationStatus;
import com.java3y.austin.billing.exception.InsufficientQuotaException;
import com.java3y.austin.billing.exception.QuotaOperationConflictException;
import com.java3y.austin.billing.repository.QuotaAccountRepository;
import com.java3y.austin.billing.repository.QuotaLedgerRepository;
import com.java3y.austin.billing.repository.QuotaReservationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class QuotaService {

    private final QuotaAccountRepository accountRepository;
    private final QuotaReservationRepository reservationRepository;
    private final QuotaLedgerRepository ledgerRepository;
    private final QuotaAlertOutboxAppender alertOutboxAppender;

    public QuotaService(QuotaAccountRepository accountRepository,
                        QuotaReservationRepository reservationRepository,
                        QuotaLedgerRepository ledgerRepository,
                        QuotaAlertOutboxAppender alertOutboxAppender) {
        this.accountRepository = accountRepository;
        this.reservationRepository = reservationRepository;
        this.ledgerRepository = ledgerRepository;
        this.alertOutboxAppender = alertOutboxAppender;
    }

    @Transactional
    public QuotaAccount initializeAccount(Long tenantId, long warningThreshold) {
        requireTenant(tenantId);
        if (warningThreshold < 0) {
            throw new IllegalArgumentException("额度告警阈值不能为负数");
        }
        return accountRepository.findByTenantId(tenantId).orElseGet(() -> {
            int now = now();
            return accountRepository.saveAndFlush(QuotaAccount.builder().tenantId(tenantId)
                    .availableQuota(0L).reservedQuota(0L).warningThreshold(warningThreshold)
                    .alertCycle(0L).thresholdAlertActive(false).insufficientAlertActive(false)
                    .created(now).updated(now).build());
        });
    }

    @Transactional
    public QuotaAccount credit(Long tenantId, long amount, String entryKey,
                               String businessType, String businessId) {
        requirePositive(amount);
        requireKey(entryKey, "额度入账幂等键不能为空");
        requireBusiness(businessType, businessId);
        QuotaAccount account = lockAccount(tenantId);
        return ledgerRepository.findByTenantIdAndEntryKey(tenantId, entryKey)
                .map(existing -> {
                    if (existing.getEntryType() != QuotaLedgerEntryType.CREDIT
                            || !Objects.equals(existing.getAvailableDelta(), amount)) {
                        throw new QuotaOperationConflictException("相同幂等键对应不同的额度入账请求");
                    }
                    return account;
                })
                .orElseGet(() -> {
                    account.setAvailableQuota(account.getAvailableQuota() + amount);
                    account.setUpdated(now());
                    resetAlertCycleIfRecovered(account);
                    saveLedger(account, null, QuotaLedgerEntryType.CREDIT, entryKey,
                            amount, 0L, businessType, businessId);
                    return account;
                });
    }

    @Transactional(noRollbackFor = InsufficientQuotaException.class)
    public QuotaReservation reserve(Long tenantId, long amount, String idempotencyKey,
                                    String businessType, String businessId) {
        return reserveWithResult(tenantId, amount, idempotencyKey, businessType, businessId)
                .getReservation();
    }

    @Transactional(noRollbackFor = InsufficientQuotaException.class)
    public QuotaReservationResult reserveWithResult(Long tenantId, long amount, String idempotencyKey,
                                                    String businessType, String businessId) {
        requirePositive(amount);
        requireKey(idempotencyKey, "额度预占幂等键不能为空");
        requireBusiness(businessType, businessId);
        QuotaAccount account = lockAccount(tenantId);
        QuotaReservation existing = reservationRepository
                .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).orElse(null);
        if (existing != null) {
            assertSameReservation(existing, amount, businessType, businessId);
            return new QuotaReservationResult(existing, false);
        }
        if (account.getAvailableQuota() < amount) {
            if (!Boolean.TRUE.equals(account.getInsufficientAlertActive())) {
                account.setInsufficientAlertActive(true);
                account.setUpdated(now());
                alertOutboxAppender.appendInsufficient(account, amount, businessType, businessId);
            }
            throw new InsufficientQuotaException("可用额度不足");
        }
        int now = now();
        account.setAvailableQuota(account.getAvailableQuota() - amount);
        account.setReservedQuota(account.getReservedQuota() + amount);
        account.setUpdated(now);
        QuotaReservation reservation = reservationRepository.saveAndFlush(QuotaReservation.builder()
                .tenantId(tenantId).idempotencyKey(idempotencyKey).businessType(businessType)
                .businessId(businessId).amount(amount).settledAmount(0L).releasedAmount(0L)
                .status(QuotaReservationStatus.RESERVED)
                .created(now).updated(now).build());
        saveLedger(account, reservation, QuotaLedgerEntryType.RESERVE, "reserve:" + idempotencyKey,
                -amount, amount, businessType, businessId);
        if (account.getAvailableQuota() <= account.getWarningThreshold()
                && !Boolean.TRUE.equals(account.getThresholdAlertActive())) {
            account.setThresholdAlertActive(true);
            alertOutboxAppender.appendThresholdReached(account, "RESERVE",
                    businessType, businessId);
        }
        return new QuotaReservationResult(reservation, true);
    }

    @Transactional(readOnly = true)
    public QuotaAccount requireAccount(Long tenantId) {
        requireTenant(tenantId);
        return accountRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("租户额度账户尚未初始化"));
    }

    @Transactional(readOnly = true)
    public Page<QuotaLedgerEntry> pageLedger(Long tenantId, QuotaLedgerEntryType entryType,
                                             int page, int perPage) {
        requireTenant(tenantId);
        int safePage = Math.max(page, 1);
        int safePerPage = Math.min(Math.max(perPage, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePerPage,
                Sort.by(Sort.Order.desc("created"), Sort.Order.desc("id")));
        if (entryType == null) {
            return ledgerRepository.findByTenantId(tenantId, pageable);
        }
        return ledgerRepository.findByTenantIdAndEntryType(tenantId, entryType, pageable);
    }

    @Transactional
    public QuotaAccount updateWarningThreshold(Long tenantId, long warningThreshold) {
        if (warningThreshold < 0) {
            throw new IllegalArgumentException("额度告警阈值不能为负数");
        }
        QuotaAccount account = lockAccount(tenantId);
        account.setWarningThreshold(warningThreshold);
        account.setUpdated(now());
        if (account.getAvailableQuota() > warningThreshold) {
            resetAlertCycleIfRecovered(account);
        } else if (!Boolean.TRUE.equals(account.getThresholdAlertActive())) {
            account.setThresholdAlertActive(true);
            alertOutboxAppender.appendThresholdReached(account, "THRESHOLD_UPDATED",
                    "QUOTA_ACCOUNT", String.valueOf(account.getId()));
        }
        return account;
    }

    @Transactional
    public QuotaReservation settle(Long tenantId, Long reservationId) {
        return completeReservation(tenantId, reservationId, true);
    }

    @Transactional
    public QuotaReservation release(Long tenantId, Long reservationId) {
        return completeReservation(tenantId, reservationId, false);
    }

    @Transactional
    public QuotaReservation settlePartially(Long tenantId, Long reservationId, long amount,
                                            String operationKey) {
        return applyReservationAmount(tenantId, reservationId, amount, operationKey, true);
    }

    @Transactional
    public QuotaReservation releasePartially(Long tenantId, Long reservationId, long amount,
                                             String operationKey) {
        return applyReservationAmount(tenantId, reservationId, amount, operationKey, false);
    }

    @Transactional
    public QuotaReservation releaseRemaining(Long tenantId, Long reservationId, String operationKey) {
        QuotaAccount account = lockAccount(tenantId);
        QuotaReservation reservation = requireReservation(tenantId, reservationId);
        String entryKey = operationEntryKey(false, reservationId, operationKey);
        QuotaLedgerEntry existing = ledgerRepository.findByTenantIdAndEntryKey(tenantId, entryKey)
                .orElse(null);
        if (existing != null) {
            assertSameCompletion(existing, reservationId, false, -existing.getReservedDelta());
            return reservation;
        }
        rejectOppositeCompletion(tenantId, reservationId, operationKey, false);
        long remaining = remainingAmount(reservation);
        if (remaining == 0) {
            return reservation;
        }
        return applyLocked(account, reservation, remaining, entryKey, false);
    }

    @Transactional
    public QuotaReservation markUnknown(Long tenantId, Long reservationId) {
        QuotaAccount account = lockAccount(tenantId);
        QuotaReservation reservation = requireReservation(tenantId, reservationId);
        if (reservation.getStatus() == QuotaReservationStatus.UNKNOWN) {
            return reservation;
        }
        if (remainingAmount(reservation) == 0) {
            throw new QuotaOperationConflictException("额度预占已全部处理，不能标记为待核对");
        }
        if (reservation.getStatus() != QuotaReservationStatus.RESERVED
                && reservation.getStatus() != QuotaReservationStatus.PARTIALLY_PROCESSED) {
            throw new QuotaOperationConflictException("当前额度预占状态不能标记为待核对");
        }
        reservation.setStatus(QuotaReservationStatus.UNKNOWN);
        reservation.setUpdated(now());
        saveLedger(account, reservation, QuotaLedgerEntryType.MARK_UNKNOWN,
                "unknown:" + reservationId, 0L, 0L,
                reservation.getBusinessType(), reservation.getBusinessId());
        return reservation;
    }

    @Transactional(readOnly = true)
    public Optional<QuotaCompletionOutcome> findCompletionOutcome(Long tenantId, Long reservationId,
                                                                  String operationKey) {
        requireTenant(tenantId);
        if (reservationId == null) {
            throw new IllegalArgumentException("额度预占单ID不能为空");
        }
        String settleKey = operationEntryKey(true, reservationId, operationKey);
        if (ledgerRepository.findByTenantIdAndEntryKey(tenantId, settleKey).isPresent()) {
            return Optional.of(QuotaCompletionOutcome.SETTLED);
        }
        String releaseKey = operationEntryKey(false, reservationId, operationKey);
        if (ledgerRepository.findByTenantIdAndEntryKey(tenantId, releaseKey).isPresent()) {
            return Optional.of(QuotaCompletionOutcome.RELEASED);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<QuotaReservationStatus> findReservationStatus(Long tenantId, Long reservationId) {
        requireTenant(tenantId);
        if (reservationId == null) {
            throw new IllegalArgumentException("额度预占单ID不能为空");
        }
        return reservationRepository.findByIdAndTenantId(reservationId, tenantId)
                .map(QuotaReservation::getStatus);
    }

    private QuotaReservation completeReservation(Long tenantId, Long reservationId, boolean settle) {
        QuotaAccount account = lockAccount(tenantId);
        QuotaReservation reservation = requireReservation(tenantId, reservationId);
        long remaining = remainingAmount(reservation);
        if (remaining == 0) {
            return reservation;
        }
        String entryKey = (settle ? "settle:" : "release:") + reservationId;
        return applyLocked(account, reservation, remaining, entryKey, settle);
    }

    private QuotaReservation applyReservationAmount(Long tenantId, Long reservationId, long amount,
                                                    String operationKey, boolean settle) {
        requirePositive(amount);
        requireKey(operationKey, "额度处理幂等键不能为空");
        QuotaAccount account = lockAccount(tenantId);
        QuotaReservation reservation = requireReservation(tenantId, reservationId);
        String entryKey = operationEntryKey(settle, reservationId, operationKey);
        QuotaLedgerEntry existing = ledgerRepository.findByTenantIdAndEntryKey(tenantId, entryKey)
                .orElse(null);
        if (existing != null) {
            assertSameCompletion(existing, reservationId, settle, amount);
            return reservation;
        }
        rejectOppositeCompletion(tenantId, reservationId, operationKey, settle);
        if (amount > remainingAmount(reservation)) {
            throw new QuotaOperationConflictException("处理额度超过预占单剩余额度");
        }
        return applyLocked(account, reservation, amount, entryKey, settle);
    }

    private void rejectOppositeCompletion(Long tenantId, Long reservationId,
                                          String operationKey, boolean settle) {
        String oppositeKey = operationEntryKey(!settle, reservationId, operationKey);
        if (ledgerRepository.findByTenantIdAndEntryKey(tenantId, oppositeKey).isPresent()) {
            throw new QuotaOperationConflictException("同一额度处理操作不能同时结算和释放");
        }
    }

    private QuotaReservation applyLocked(QuotaAccount account, QuotaReservation reservation, long amount,
                                         String entryKey, boolean settle) {
        if (account.getReservedQuota() < amount) {
            throw new IllegalStateException("账户预占余额与预占单不一致");
        }
        long availableDelta = settle ? 0L : amount;
        account.setAvailableQuota(account.getAvailableQuota() + availableDelta);
        account.setReservedQuota(account.getReservedQuota() - amount);
        account.setUpdated(now());
        if (settle) {
            reservation.setSettledAmount(processedAmount(reservation.getSettledAmount()) + amount);
        } else {
            reservation.setReleasedAmount(processedAmount(reservation.getReleasedAmount()) + amount);
        }
        updateReservationStatus(reservation);
        reservation.setUpdated(now());
        QuotaLedgerEntryType type = settle ? QuotaLedgerEntryType.SETTLE : QuotaLedgerEntryType.RELEASE;
        saveLedger(account, reservation, type, entryKey,
                availableDelta, -amount, reservation.getBusinessType(), reservation.getBusinessId());
        return reservation;
    }

    private void updateReservationStatus(QuotaReservation reservation) {
        long settled = processedAmount(reservation.getSettledAmount());
        long released = processedAmount(reservation.getReleasedAmount());
        if (settled + released < reservation.getAmount()) {
            reservation.setStatus(QuotaReservationStatus.PARTIALLY_PROCESSED);
        } else if (settled == reservation.getAmount()) {
            reservation.setStatus(QuotaReservationStatus.SETTLED);
        } else if (released == reservation.getAmount()) {
            reservation.setStatus(QuotaReservationStatus.RELEASED);
        } else {
            reservation.setStatus(QuotaReservationStatus.COMPLETED);
        }
    }

    private long remainingAmount(QuotaReservation reservation) {
        long remaining = reservation.getAmount() - processedAmount(reservation.getSettledAmount())
                - processedAmount(reservation.getReleasedAmount());
        if (remaining < 0) {
            throw new IllegalStateException("额度预占单已处理数量超过预占总量");
        }
        return remaining;
    }

    private long processedAmount(Long amount) {
        return amount == null ? 0L : amount;
    }

    private String operationEntryKey(boolean settle, Long reservationId, String operationKey) {
        requireKey(operationKey, "额度处理幂等键不能为空");
        if (operationKey.length() > 100) {
            throw new IllegalArgumentException("额度处理幂等键长度不能超过100");
        }
        return (settle ? "settle:" : "release:") + reservationId + ":" + operationKey;
    }

    private void assertSameCompletion(QuotaLedgerEntry existing, Long reservationId,
                                      boolean settle, long amount) {
        QuotaLedgerEntryType expectedType = settle
                ? QuotaLedgerEntryType.SETTLE : QuotaLedgerEntryType.RELEASE;
        long expectedAvailableDelta = settle ? 0L : amount;
        if (existing.getEntryType() != expectedType
                || !Objects.equals(existing.getReservationId(), reservationId)
                || !Objects.equals(existing.getReservedDelta(), -amount)
                || !Objects.equals(existing.getAvailableDelta(), expectedAvailableDelta)) {
            throw new QuotaOperationConflictException("相同幂等键对应不同的额度处理请求");
        }
    }

    private QuotaAccount lockAccount(Long tenantId) {
        requireTenant(tenantId);
        return accountRepository.findWithLockByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("租户额度账户尚未初始化"));
    }

    private QuotaReservation requireReservation(Long tenantId, Long reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("额度预占单ID不能为空");
        }
        return reservationRepository.findByIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("额度预占单不存在"));
    }

    private void saveLedger(QuotaAccount account, QuotaReservation reservation,
                            QuotaLedgerEntryType type, String entryKey,
                            long availableDelta, long reservedDelta,
                            String businessType, String businessId) {
        ledgerRepository.save(QuotaLedgerEntry.builder().tenantId(account.getTenantId())
                .accountId(account.getId()).reservationId(reservation == null ? null : reservation.getId())
                .entryType(type).entryKey(entryKey).availableDelta(availableDelta)
                .reservedDelta(reservedDelta).availableAfter(account.getAvailableQuota())
                .reservedAfter(account.getReservedQuota()).businessType(businessType)
                .businessId(businessId).created(now()).build());
    }

    private void assertSameReservation(QuotaReservation existing, long amount,
                                       String businessType, String businessId) {
        if (!Objects.equals(existing.getAmount(), amount)
                || !Objects.equals(existing.getBusinessType(), businessType)
                || !Objects.equals(existing.getBusinessId(), businessId)) {
            throw new QuotaOperationConflictException("相同幂等键对应不同的额度预占请求");
        }
    }

    private void resetAlertCycleIfRecovered(QuotaAccount account) {
        if (account.getAvailableQuota() <= account.getWarningThreshold()) {
            return;
        }
        if (Boolean.TRUE.equals(account.getThresholdAlertActive())
                || Boolean.TRUE.equals(account.getInsufficientAlertActive())) {
            long cycle = account.getAlertCycle() == null ? 0L : account.getAlertCycle();
            account.setAlertCycle(cycle + 1);
            account.setThresholdAlertActive(false);
            account.setInsufficientAlertActive(false);
        }
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
    }

    private void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("额度数量必须大于0");
        }
    }

    private void requireKey(String key, String message) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireBusiness(String businessType, String businessId) {
        requireKey(businessType, "额度业务类型不能为空");
        requireKey(businessId, "额度业务ID不能为空");
    }

    private int now() {
        return Math.toIntExact(Instant.now().getEpochSecond());
    }
}
