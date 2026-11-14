package com.java3y.austin.report.repository;

import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.domain.ReportStatus;
import com.java3y.austin.report.domain.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReportGenerationJobRepository extends JpaRepository<ReportGenerationJob, Long> {
    Optional<ReportGenerationJob> findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
            Long tenantId, ReportType reportType, LocalDate periodStart, LocalDate periodEnd);

    Optional<ReportGenerationJob> findByIdAndTenantId(Long id, Long tenantId);

    Page<ReportGenerationJob> findAllByTenantIdOrderByPeriodStartDescIdDesc(
            Long tenantId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ReportGenerationJob job where job.id = :id")
    Optional<ReportGenerationJob> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ReportGenerationJob job where job.id = :id and job.tenantId = :tenantId")
    Optional<ReportGenerationJob> findLockedByIdAndTenantId(@Param("id") Long id,
                                                            @Param("tenantId") Long tenantId);

    @Modifying
    @Query(value = "INSERT IGNORE INTO report_generation_job "
            + "(tenant_id, report_type, period_start, period_end, status, attempt_count, max_attempts, "
            + "replay_count, created, updated, version) VALUES "
            + "(:tenantId, :reportType, :periodStart, :periodEnd, 'PENDING', 0, :maxAttempts, 0, "
            + ":now, :now, 0)", nativeQuery = true)
    int insertIgnore(@Param("tenantId") Long tenantId, @Param("reportType") String reportType,
                     @Param("periodStart") LocalDate periodStart,
                     @Param("periodEnd") LocalDate periodEnd,
                     @Param("maxAttempts") Integer maxAttempts, @Param("now") Integer now);

    @Query("select job.id from ReportGenerationJob job where job.status = :pending "
            + "or (job.status = :retryWait and job.nextRetryAt <= :now) "
            + "or (job.status = :processing and job.lockedUntil <= :now) order by job.id")
    List<Long> findReadyIds(@Param("pending") ReportStatus pending,
                            @Param("retryWait") ReportStatus retryWait,
                            @Param("processing") ReportStatus processing,
                            @Param("now") Integer now, Pageable pageable);
}
