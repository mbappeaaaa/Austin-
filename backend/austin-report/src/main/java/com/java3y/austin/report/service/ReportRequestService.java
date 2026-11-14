package com.java3y.austin.report.service;

import com.java3y.austin.auth.domain.Tenant;
import com.java3y.austin.auth.repository.TenantRepository;
import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.domain.ReportType;
import com.java3y.austin.report.repository.ReportGenerationJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

@Service
public class ReportRequestService {
    private final ReportGenerationJobRepository repository;
    private final TenantRepository tenantRepository;
    private final Clock clock;

    @Autowired
    public ReportRequestService(ReportGenerationJobRepository repository,
                                TenantRepository tenantRepository) {
        this(repository, tenantRepository, Clock.systemUTC());
    }

    ReportRequestService(ReportGenerationJobRepository repository,
                         TenantRepository tenantRepository, Clock clock) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.clock = clock;
    }

    @Transactional
    public ReportGenerationJob requestMonthly(Long tenantId, YearMonth month) {
        Tenant tenant = requireTenant(tenantId);
        if (month == null) {
            throw new IllegalArgumentException("报告月份不能为空");
        }
        YearMonth currentMonth = YearMonth.now(clock.withZone(ZoneId.of(tenant.getTimezone())));
        if (!month.isBefore(currentMonth)) {
            throw new IllegalArgumentException("只能生成已经结束的自然月报告");
        }
        java.time.LocalDate start = month.atDay(1);
        java.time.LocalDate end = month.plusMonths(1).atDay(1);
        int now = now();
        repository.insertIgnore(tenantId, ReportType.MONTHLY_USAGE_BILLING.name(),
                start, end, 3, now);
        return repository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
                        tenantId, ReportType.MONTHLY_USAGE_BILLING, start, end)
                .orElseThrow(() -> new IllegalStateException("月报任务创建后无法读取"));
    }

    @Transactional(readOnly = true)
    public Page<ReportGenerationJob> list(Long tenantId, int page, int pageSize) {
        requireTenantId(tenantId);
        if (page <= 0 || pageSize <= 0 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数无效，每页最多100条");
        }
        return repository.findAllByTenantIdOrderByPeriodStartDescIdDesc(
                tenantId, PageRequest.of(page - 1, pageSize));
    }

    @Transactional(readOnly = true)
    public ReportGenerationJob requireTenantJob(Long tenantId, Long reportId) {
        requireTenantId(tenantId);
        return repository.findByIdAndTenantId(reportId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
    }

    private Tenant requireTenant(Long tenantId) {
        requireTenantId(tenantId);
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("租户不存在"));
    }

    private void requireTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
    }

    private int now() {
        return Math.toIntExact(Instant.now(clock).getEpochSecond());
    }
}
