package com.java3y.austin.web.service;

import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import com.java3y.austin.billing.outbox.OutboxMessage;
import com.java3y.austin.report.service.ReportJobStateService;
import com.java3y.austin.support.service.InAppMessageService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportCompletedNotificationHandlerTest {
    @Test
    void shouldNotifyTenantAdminsWithAuthenticatedDownloadPath() {
        SysUserRepository users = mock(SysUserRepository.class);
        InAppMessageService messages = mock(InAppMessageService.class);
        when(users.findAllByTenantIdAndRoleAndEnabledTrueOrderByIdAsc(7L, "ADMIN"))
                .thenReturn(Collections.singletonList(SysUser.builder().username("admin").build()));
        ReportCompletedNotificationHandler handler =
                new ReportCompletedNotificationHandler(users, messages);

        handler.handle(message());

        verify(messages).storeSystemMessage(eq(7L), eq(Collections.singletonList("admin")),
                eq("月度消息与账务报告已生成"), contains("2026-07-31"),
                eq("/billing/reports/11/download"), eq("report-completed:11"));
    }

    private OutboxMessage message() {
        String payload = "{\"tenantId\":7,\"reportId\":11,"
                + "\"periodStart\":\"2026-07-01\",\"periodEnd\":\"2026-08-01\","
                + "\"downloadPath\":\"/billing/reports/11/download\"}";
        return OutboxMessage.builder().id(21L).tenantId(7L).aggregateType("REPORT")
                .aggregateId("11").eventType(ReportJobStateService.REPORT_COMPLETED_EVENT)
                .eventKey("report-completed:11").payload(payload).build();
    }
}
