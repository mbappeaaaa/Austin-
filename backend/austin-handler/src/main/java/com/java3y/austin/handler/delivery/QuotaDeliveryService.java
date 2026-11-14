package com.java3y.austin.handler.delivery;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.java3y.austin.billing.domain.QuotaReservationStatus;
import com.java3y.austin.billing.service.QuotaService;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.handler.handler.DeliveryResult;
import com.java3y.austin.handler.handler.DeliveryStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
public class QuotaDeliveryService {

    private final QuotaService quotaService;

    public QuotaDeliveryService(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    public boolean isAlreadyCompleted(TaskInfo taskInfo, String operationKey) {
        if (!hasReservation(taskInfo)) {
            return false;
        }
        QuotaReservationStatus status = quotaService.findReservationStatus(
                        taskInfo.getTenantId(), taskInfo.getQuotaReservationId())
                .orElseThrow(() -> new NonRetryableDeliveryException(
                        "QUOTA_RESERVATION_MISSING", "额度预占单不存在"));
        if (status == QuotaReservationStatus.UNKNOWN) {
            throw new NonRetryableDeliveryException(
                    "QUOTA_RECONCILIATION_REQUIRED", "投递结果待人工核对，禁止自动重发");
        }
        if (status == QuotaReservationStatus.SETTLED
                || status == QuotaReservationStatus.RELEASED
                || status == QuotaReservationStatus.COMPLETED) {
            return true;
        }
        return quotaService.findCompletionOutcome(taskInfo.getTenantId(),
                taskInfo.getQuotaReservationId(), operationKey).isPresent();
    }

    public void complete(TaskInfo taskInfo, long amount, String operationKey, DeliveryResult result) {
        if (amount <= 0) {
            throw new IllegalArgumentException("投递结果对应的额度数量必须大于0");
        }
        DeliveryStatus status = result.getStatus();
        if (status == DeliveryStatus.RETRYABLE_FAILURE) {
            throw new RetryableDeliveryException(reason(result, "渠道暂时不可用"));
        }
        if (status == DeliveryStatus.UNKNOWN) {
            markUnknownBestEffort(taskInfo);
            throw new NonRetryableDeliveryException(reasonCode(result, "DELIVERY_RESULT_UNKNOWN"),
                    reason(result, "渠道投递结果不确定，需要人工核对"));
        }
        if (!hasReservation(taskInfo) || status == DeliveryStatus.DEFERRED) {
            return;
        }
        try {
            if (status == DeliveryStatus.SUCCESS) {
                quotaService.settlePartially(taskInfo.getTenantId(), taskInfo.getQuotaReservationId(),
                        amount, operationKey);
            } else if (status == DeliveryStatus.FINAL_FAILURE || status == DeliveryStatus.SKIPPED) {
                quotaService.releasePartially(taskInfo.getTenantId(), taskInfo.getQuotaReservationId(),
                        amount, operationKey);
            }
        } catch (RuntimeException settlementFailure) {
            markUnknownBestEffort(taskInfo);
            throw new NonRetryableDeliveryException("QUOTA_SETTLEMENT_UNCERTAIN",
                    "渠道已返回结果，但额度结算状态不确定", settlementFailure);
        }
    }

    public void releaseRemaining(TaskInfo taskInfo, String reasonCode) {
        if (!hasReservation(taskInfo)) {
            return;
        }
        quotaService.releaseRemaining(taskInfo.getTenantId(), taskInfo.getQuotaReservationId(),
                operationKey(taskInfo, "remaining:" + reasonCode, Collections.<String>emptyList()));
    }

    public String receiverOperationKey(TaskInfo taskInfo, String receiver) {
        return operationKey(taskInfo, "receiver", Collections.singletonList(receiver));
    }

    public String batchOperationKey(TaskInfo taskInfo, Collection<String> receivers) {
        return operationKey(taskInfo, "batch", receivers);
    }

    private String operationKey(TaskInfo taskInfo, String scope, Collection<String> receivers) {
        List<String> sorted = new ArrayList<>();
        if (CollUtil.isNotEmpty(receivers)) {
            sorted.addAll(receivers);
            Collections.sort(sorted);
        }
        String messageIdentity = CharSequenceUtil.isNotBlank(taskInfo.getMessageId())
                ? taskInfo.getMessageId() : String.valueOf(taskInfo.getBusinessId());
        return "delivery:" + DigestUtil.sha256Hex(messageIdentity + "|" + scope + "|"
                + String.join(",", sorted));
    }

    private void markUnknown(TaskInfo taskInfo) {
        if (hasReservation(taskInfo)) {
            quotaService.markUnknown(taskInfo.getTenantId(), taskInfo.getQuotaReservationId());
        }
    }

    private void markUnknownBestEffort(TaskInfo taskInfo) {
        try {
            markUnknown(taskInfo);
        } catch (RuntimeException ignored) {
            // 原异常更能说明结算状态；Inbox 会转死信，后续由额度流水人工核对。
        }
    }

    private boolean hasReservation(TaskInfo taskInfo) {
        return taskInfo.getTenantId() != null && taskInfo.getQuotaReservationId() != null;
    }

    private String reasonCode(DeliveryResult result, String fallback) {
        return CharSequenceUtil.isNotBlank(result.getReasonCode()) ? result.getReasonCode() : fallback;
    }

    private String reason(DeliveryResult result, String fallback) {
        return reasonCode(result, fallback);
    }
}
