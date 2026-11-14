package com.java3y.austin.handler.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

    Optional<InboxMessage> findByTenantIdAndConsumerGroupAndConsumeKey(
            Long tenantId, String consumerGroup, String consumeKey);

    Page<InboxMessage> findByTenantIdAndStatusOrderByUpdatedDescIdDesc(
            Long tenantId, InboxStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from InboxMessage message where message.id = :id and message.tenantId = :tenantId")
    Optional<InboxMessage> findLockedByIdAndTenantId(@Param("id") Long id,
                                                     @Param("tenantId") Long tenantId);

    @Modifying
    @Query(value = "INSERT IGNORE INTO mq_consume_inbox "
            + "(tenant_id, consumer_group, consume_key, payload_hash, payload, status, attempt_count, "
            + "max_attempts, replay_count, created, updated, version) "
            + "VALUES (:tenantId, :consumerGroup, :consumeKey, :payloadHash, :payload, 'PENDING', 0, "
            + ":maxAttempts, 0, :now, :now, 0)", nativeQuery = true)
    int insertIgnore(@Param("tenantId") Long tenantId,
                     @Param("consumerGroup") String consumerGroup,
                     @Param("consumeKey") String consumeKey,
                     @Param("payloadHash") String payloadHash,
                     @Param("payload") String payload,
                     @Param("maxAttempts") Integer maxAttempts,
                     @Param("now") Integer now);

    @Query("select message from InboxMessage message where message.status = :pending "
            + "or (message.status = :retryWait and message.nextRetryAt <= :now) "
            + "or (message.status = :processing and message.lockedUntil <= :now) order by message.id")
    List<InboxMessage> findReady(@Param("pending") InboxStatus pending,
                                 @Param("retryWait") InboxStatus retryWait,
                                 @Param("processing") InboxStatus processing,
                                 @Param("now") Long now,
                                 Pageable pageable);
}
