package com.java3y.austin.web.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import com.java3y.austin.billing.outbox.OutboxEventHandler;
import com.java3y.austin.billing.outbox.OutboxMessage;
import com.java3y.austin.billing.service.QuotaAlertOutboxAppender;
import com.java3y.austin.support.service.InAppMessageService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class QuotaInsufficientNotificationHandler implements OutboxEventHandler {
    private final SysUserRepository userRepository;
    private final InAppMessageService inAppMessageService;

    public QuotaInsufficientNotificationHandler(SysUserRepository userRepository,
                                                InAppMessageService inAppMessageService) {
        this.userRepository = userRepository;
        this.inAppMessageService = inAppMessageService;
    }

    @Override
    public String eventType() {
        return QuotaAlertOutboxAppender.INSUFFICIENT_EVENT;
    }

    @Override
    public void handle(OutboxMessage message) {
        JSONObject payload = JSON.parseObject(message.getPayload());
        Long tenantId = payload.getLong("tenantId");
        Long accountId = payload.getLong("accountId");
        Long cycle = payload.getLong("alertCycle");
        Long available = payload.getLong("availableQuota");
        Long reserved = payload.getLong("reservedQuota");
        Long requested = payload.getLong("requestedAmount");
        if (tenantId == null || accountId == null || cycle == null || cycle < 0
                || !Objects.equals(message.getTenantId(), tenantId)
                || !Objects.equals(message.getAggregateId(), String.valueOf(accountId))
                || !Objects.equals(message.getEventKey(), "quota-insufficient:" + tenantId + ":" + cycle)
                || available == null || available < 0 || reserved == null || reserved < 0
                || requested == null || requested <= 0) {
            throw new IllegalArgumentException("额度不足Outbox载荷与事件信封不一致");
        }
        List<String> receivers = admins(tenantId);
        String content = "本次发送需要消息额度 " + requested + "，当前可用额度仅 " + available
                + "，发送已被阻止。请购买额度后重试；当前另有预占额度 " + reserved + "。";
        inAppMessageService.storeSystemMessage(tenantId, receivers,
                "消息额度不足，发送已阻止", content, "/billing/quota", message.getEventKey());
    }

    private List<String> admins(Long tenantId) {
        List<String> receivers = userRepository
                .findAllByTenantIdAndRoleAndEnabledTrueOrderByIdAsc(tenantId, "ADMIN")
                .stream().map(SysUser::getUsername).collect(Collectors.toList());
        if (receivers.isEmpty()) {
            throw new IllegalStateException("租户没有可接收额度告警的管理员");
        }
        return receivers;
    }
}
