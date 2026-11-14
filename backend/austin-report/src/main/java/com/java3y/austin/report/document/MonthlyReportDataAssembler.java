package com.java3y.austin.report.document;

import com.java3y.austin.auth.domain.Tenant;
import com.java3y.austin.auth.repository.TenantRepository;
import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.domain.QuotaLedgerEntry;
import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.repository.CurrencyFeeProjection;
import com.java3y.austin.billing.payment.repository.PaymentOrderRepository;
import com.java3y.austin.billing.repository.QuotaAccountRepository;
import com.java3y.austin.billing.repository.QuotaLedgerRepository;
import com.java3y.austin.billing.repository.TemplateUsageProjection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MonthlyReportDataAssembler {
    private final TenantRepository tenantRepository;
    private final QuotaAccountRepository accountRepository;
    private final QuotaLedgerRepository ledgerRepository;
    private final PaymentOrderRepository paymentRepository;

    @Value("${austin.report.max-ledger-rows:5000}")
    private int maxLedgerRows;
    @Value("${austin.report.max-payment-detail-rows:1000}")
    private int maxPaymentRows;

    public MonthlyReportDataAssembler(TenantRepository tenantRepository,
                                      QuotaAccountRepository accountRepository,
                                      QuotaLedgerRepository ledgerRepository,
                                      PaymentOrderRepository paymentRepository) {
        this.tenantRepository = tenantRepository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public MonthlyReportData assemble(Long tenantId, java.time.LocalDate periodStart,
                                      java.time.LocalDate periodEnd) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("租户不存在"));
        ZoneId zone = ZoneId.of(tenant.getTimezone());
        int start = Math.toIntExact(periodStart.atStartOfDay(zone).toEpochSecond());
        int end = Math.toIntExact(periodEnd.atStartOfDay(zone).toEpochSecond());
        int ledgerLimit = bounded(maxLedgerRows, 1, 5000);
        int paymentLimit = bounded(maxPaymentRows, 1, 1000);
        Page<QuotaLedgerEntry> ledgerPage = ledgerRepository
                .findAllByTenantIdAndCreatedGreaterThanEqualAndCreatedLessThanOrderByCreatedAscIdAsc(
                        tenantId, start, end, PageRequest.of(0, ledgerLimit));
        Page<PaymentOrder> paymentPage = paymentRepository
                .findAllByTenantIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAscIdAsc(
                        tenantId, PaymentOrderStatus.SUCCEEDED, start, end,
                        PageRequest.of(0, paymentLimit));
        QuotaAccount account = accountRepository.findByTenantId(tenantId).orElse(null);

        return MonthlyReportData.builder().tenantId(tenantId).tenantName(tenant.getName())
                .tenantTimezone(tenant.getTimezone()).periodStart(periodStart).periodEnd(periodEnd)
                .settledUsage(defaultLong(ledgerRepository.sumSettledUsage(tenantId, start, end)))
                .purchasedQuota(defaultLong(ledgerRepository.sumCredits(tenantId, "PAYMENT", start, end)))
                .availableQuota(account == null ? 0L : account.getAvailableQuota())
                .reservedQuota(account == null ? 0L : account.getReservedQuota())
                .templateUsage(templateUsage(tenantId, start, end))
                .feeSummaries(feeSummaries(tenantId, start, end))
                .paymentDetails(paymentPage.getContent().stream().map(this::paymentRow)
                        .collect(Collectors.toList()))
                .paymentDetailsTruncated(paymentPage.getTotalElements() > paymentPage.getNumberOfElements())
                .quotaLedger(ledgerPage.getContent().stream().map(this::ledgerRow)
                        .collect(Collectors.toList()))
                .quotaLedgerTruncated(ledgerPage.getTotalElements() > ledgerPage.getNumberOfElements())
                .build();
    }

    private List<TemplateUsageRow> templateUsage(Long tenantId, int start, int end) {
        return ledgerRepository.summarizeTemplateUsage(tenantId, start, end).stream()
                .map(row -> new TemplateUsageRow(row.getTemplateId(), defaultLong(row.getUsageAmount())))
                .collect(Collectors.toList());
    }

    private List<FeeSummaryRow> feeSummaries(Long tenantId, int start, int end) {
        return paymentRepository.summarizeSucceededFees(tenantId, start, end).stream()
                .map(row -> new FeeSummaryRow(row.getCurrency(), defaultLong(row.getAmountMinor()),
                        defaultLong(row.getOrderCount())))
                .collect(Collectors.toList());
    }

    private PaymentDetailRow paymentRow(PaymentOrder order) {
        return new PaymentDetailRow(order.getOrderNo(), order.getProvider().name(), order.getCurrency(),
                order.getAmountMinor(), order.getQuotaAmount(), order.getPaidAt());
    }

    private QuotaLedgerRow ledgerRow(QuotaLedgerEntry entry) {
        return new QuotaLedgerRow(entry.getId(), entry.getEntryType().name(),
                entry.getAvailableDelta(), entry.getReservedDelta(), entry.getAvailableAfter(),
                entry.getReservedAfter(), entry.getBusinessType(), entry.getBusinessId(),
                entry.getCreated());
    }

    private int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
