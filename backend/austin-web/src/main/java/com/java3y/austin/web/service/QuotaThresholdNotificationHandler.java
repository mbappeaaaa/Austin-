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
public class QuotaThresholdNotificationHandler implements OutboxEventHandler {
    private final SysUserRepository userRepository;
    private final InAppMessageService inAppMessageService;

    public QuotaThresholdNotificationHandler(SysUserRepository userRepository,
                                             InAppMessageService inAppMessageService) {
        this.userRepository = userRepository;
        this.inAppMessageService = inAppMessageService;
    }

    @Override
    public String eventType() {
        return QuotaAlertOutboxAppender.THRESHOLD_REACHED_EVENT;
    }

    @Override
    public void handle(OutboxMessage message) {
        JSONObject payload = JSON.parseObject(message.getPayload());
        Long tenantId = payload.getLong("tenantId");
        Long accountId = payload.getLong("accountId");
        Long cycle = payload.getLong("alertCycle");
        Long available = payload.getLong("availableQuota");
        Long reserved = payload.getLong("reservedQuota");
        Long threshold = payload.getLong("warningThreshold");
        if (tenantId == null || accountId == null || cycle == null || cycle < 0
                || !Objects.equals(message.getTenantId(), tenantId)
                || !Objects.equals(message.getAggregateId(), String.valueOf(accountId))
                || !Objects.equals(message.getEventKey(), "quota-threshold:" + tenantId + ":" + cycle)
                || available == null || available < 0 || reserved == null || reserved < 0
                || threshold == null || threshold < 0) {
            throw new IllegalArgumentException("额度阈值Outbox载荷与事件信封不一致");
        }
        List<String> receivers = admins(tenantId);
        String content = "当前可用消息额度为 " + available + "，预占额度为 " + reserved
                + "，已达到告警阈值 " + threshold + "。请及时购买额度，避免消息发送受阻。";
        inAppMessageService.storeSystemMessage(tenantId, receivers,
                "消息额度已达到告警阈值", content, "/billing/quota", message.getEventKey());
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
