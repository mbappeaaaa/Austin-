package com.java3y.austin.billing.service;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.domain.OutboxStatus;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class QuotaAlertOutboxAppender {
    public static final String THRESHOLD_REACHED_EVENT = "billing.quota.threshold-reached.v1";
    public static final String INSUFFICIENT_EVENT = "billing.quota.insufficient.v1";

    private final OutboxEventRepository repository;

    public QuotaAlertOutboxAppender(OutboxEventRepository repository) {
        this.repository = repository;
    }

    public void appendThresholdReached(QuotaAccount account, String reason,
                                       String businessType, String businessId) {
        append(account, THRESHOLD_REACHED_EVENT, "quota-threshold:", null,
                reason, businessType, businessId);
    }

    public void appendInsufficient(QuotaAccount account, long requestedAmount,
                                   String businessType, String businessId) {
        append(account, INSUFFICIENT_EVENT, "quota-insufficient:", requestedAmount,
                "RESERVE_REJECTED", businessType, businessId);
    }

    private void append(QuotaAccount account, String eventType, String keyPrefix,
                        Long requestedAmount, String reason,
                        String businessType, String businessId) {
        long cycle = account.getAlertCycle() == null ? 0L : account.getAlertCycle();
        String eventKey = keyPrefix + account.getTenantId() + ":" + cycle;
        if (repository.findByEventKey(eventKey).isPresent()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", account.getTenantId());
        payload.put("accountId", account.getId());
        payload.put("alertCycle", cycle);
        payload.put("availableQuota", account.getAvailableQuota());
        payload.put("reservedQuota", account.getReservedQuota());
        payload.put("warningThreshold", account.getWarningThreshold());
        if (requestedAmount != null) {
            payload.put("requestedAmount", requestedAmount);
        }
        payload.put("reason", reason);
        payload.put("businessType", businessType);
        payload.put("businessId", businessId);
        int now = Math.toIntExact(Instant.now().getEpochSecond());
        repository.save(OutboxEvent.builder().tenantId(account.getTenantId())
                .aggregateType("QUOTA_ACCOUNT").aggregateId(String.valueOf(account.getId()))
                .eventType(eventType).eventKey(eventKey).payload(JSON.toJSONString(payload))
                .status(OutboxStatus.PENDING).attemptCount(0).maxAttempts(5).replayCount(0)
                .created(now).updated(now).build());
    }
}

