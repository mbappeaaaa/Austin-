package com.java3y.austin.report.service;

import com.java3y.austin.auth.domain.Tenant;
import com.java3y.austin.auth.repository.TenantRepository;
import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.domain.ReportStatus;
import com.java3y.austin.report.domain.ReportType;
import com.java3y.austin.report.repository.ReportGenerationJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportRequestServiceTest {
    @Test
    void shouldCreateOnlyClosedMonthWithDatabaseIdempotency() {
        ReportGenerationJobRepository jobs = mock(ReportGenerationJobRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        Tenant tenant = Tenant.builder().id(7L).timezone("Asia/Taipei").build();
        ReportGenerationJob job = ReportGenerationJob.builder().id(11L).tenantId(7L)
                .reportType(ReportType.MONTHLY_USAGE_BILLING).status(ReportStatus.PENDING).build();
        when(tenants.findById(7L)).thenReturn(Optional.of(tenant));
        when(jobs.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(7L,
                ReportType.MONTHLY_USAGE_BILLING, LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 1))).thenReturn(Optional.of(job));
        ReportRequestService service = new ReportRequestService(jobs, tenants,
                Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC));

        assertEquals(11L, service.requestMonthly(7L, YearMonth.of(2026, 7)).getId());
        verify(jobs).insertIgnore(7L, ReportType.MONTHLY_USAGE_BILLING.name(),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), 3, 1788048000);
        assertThrows(IllegalArgumentException.class,
                () -> service.requestMonthly(7L, YearMonth.of(2026, 8)));
    }
}
