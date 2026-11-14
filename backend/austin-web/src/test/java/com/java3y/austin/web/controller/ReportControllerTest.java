package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.domain.ReportStatus;
import com.java3y.austin.report.domain.ReportType;
import com.java3y.austin.report.service.ReportRequestService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportControllerTest {
    @Test
    void shouldTakeTenantFromAuthenticatedPrincipal() {
        ReportRequestService reports = mock(ReportRequestService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.tenantId()).thenReturn(7L);
        ReportGenerationJob job = ReportGenerationJob.builder().id(11L).tenantId(7L)
                .reportType(ReportType.MONTHLY_USAGE_BILLING)
                .periodStart(LocalDate.of(2026, 7, 1)).periodEnd(LocalDate.of(2026, 8, 1))
                .status(ReportStatus.PENDING).attemptCount(0).replayCount(0).created(1).build();
        when(reports.requestMonthly(7L, YearMonth.of(2026, 7))).thenReturn(job);
        ReportController controller = new ReportController(reports, currentUser);

        assertEquals(11L, controller.requestMonthly("2026-07").getId());
        verify(reports).requestMonthly(7L, YearMonth.of(2026, 7));
    }
}
