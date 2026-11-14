package com.java3y.austin.billing.payment.repository;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    Optional<OutboxEvent> findByEventKey(String eventKey);

    Page<OutboxEvent> findByTenantIdAndStatusOrderByUpdatedDescIdDesc(
            Long tenantId, com.java3y.austin.billing.payment.domain.OutboxStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :id")
    Optional<OutboxEvent> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :id and event.tenantId = :tenantId")
    Optional<OutboxEvent> findLockedByIdAndTenantId(@Param("id") Long id,
                                                    @Param("tenantId") Long tenantId);

    @Query("select event.id from OutboxEvent event where event.status = :pending "
            + "or (event.status = :retryWait and event.nextRetryAt <= :now) "
            + "or (event.status = :processing and event.lockedUntil <= :now) "
            + "order by event.id")
    List<Long> findReadyIds(@Param("pending") com.java3y.austin.billing.payment.domain.OutboxStatus pending,
                            @Param("retryWait") com.java3y.austin.billing.payment.domain.OutboxStatus retryWait,
                            @Param("processing") com.java3y.austin.billing.payment.domain.OutboxStatus processing,
                            @Param("now") Integer now, Pageable pageable);
}
