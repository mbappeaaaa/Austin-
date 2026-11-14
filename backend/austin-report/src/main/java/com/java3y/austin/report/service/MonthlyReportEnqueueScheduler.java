package com.java3y.austin.report.service;

import com.java3y.austin.auth.domain.Tenant;
import com.java3y.austin.auth.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;

@Slf4j
@Component
@ConditionalOnProperty(name = "austin.report.monthly-auto-enqueue-enabled",
        havingValue = "true", matchIfMissing = true)
public class MonthlyReportEnqueueScheduler {
    private final TenantRepository tenantRepository;
    private final ReportRequestService requestService;

    public MonthlyReportEnqueueScheduler(TenantRepository tenantRepository,
                                         ReportRequestService requestService) {
        this.tenantRepository = tenantRepository;
        this.requestService = requestService;
    }

    @Scheduled(cron = "${austin.report.monthly-enqueue-cron:0 15 2 * * *}", zone = "UTC")
    public void enqueuePreviousMonth() {
        for (Tenant tenant : tenantRepository.findAllByStatusOrderByIdAsc("ACTIVE")) {
            try {
                YearMonth previous = YearMonth.now(ZoneId.of(tenant.getTimezone())).minusMonths(1);
                requestService.requestMonthly(tenant.getId(), previous);
            } catch (RuntimeException exception) {
                log.error("Monthly report enqueue failed, tenantId:{}, failureType:{}",
                        tenant.getId(), exception.getClass().getSimpleName());
            }
        }
    }
}
