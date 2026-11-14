package com.java3y.austin.web.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import com.java3y.austin.billing.outbox.OutboxEventHandler;
import com.java3y.austin.billing.outbox.OutboxMessage;
import com.java3y.austin.report.service.ReportJobStateService;
import com.java3y.austin.support.service.InAppMessageService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Component
public class ReportCompletedNotificationHandler implements OutboxEventHandler {
    private final SysUserRepository userRepository;
    private final InAppMessageService inAppMessageService;

    public ReportCompletedNotificationHandler(SysUserRepository userRepository,
                                              InAppMessageService inAppMessageService) {
        this.userRepository = userRepository;
        this.inAppMessageService = inAppMessageService;
    }

    @Override
    public String eventType() {
        return ReportJobStateService.REPORT_COMPLETED_EVENT;
    }

    @Override
    public void handle(OutboxMessage message) {
        JSONObject payload = JSON.parseObject(message.getPayload());
        Long tenantId = payload.getLong("tenantId");
        Long reportId = payload.getLong("reportId");
        String periodStart = payload.getString("periodStart");
        String periodEnd = payload.getString("periodEnd");
        String downloadPath = payload.getString("downloadPath");
        if (!Objects.equals(message.getTenantId(), tenantId)
                || !Objects.equals(message.getAggregateId(), String.valueOf(reportId))
                || periodStart == null || periodEnd == null || downloadPath == null) {
            throw new IllegalArgumentException("报告完成Outbox载荷与事件信封不一致");
        }
        List<String> receivers = userRepository
                .findAllByTenantIdAndRoleAndEnabledTrueOrderByIdAsc(tenantId, "ADMIN")
                .stream().map(SysUser::getUsername).collect(Collectors.toList());
        if (receivers.isEmpty()) {
            throw new IllegalStateException("租户没有可接收报告通知的管理员");
        }
        String content = "您申请的 " + periodStart + " 至 "
                + LocalDate.parse(periodEnd).minusDays(1)
                + " 月度消息用量与账务报告已生成，可点击链接下载。";
        inAppMessageService.storeSystemMessage(tenantId, receivers,
                "月度消息与账务报告已生成", content, downloadPath, message.getEventKey());
    }
}
