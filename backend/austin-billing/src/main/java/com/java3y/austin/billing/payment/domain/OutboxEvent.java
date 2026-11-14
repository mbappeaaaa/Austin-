package com.java3y.austin.billing.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Version;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "billing_outbox_event")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;
    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;
    @Column(name = "event_key", nullable = false, length = 160, unique = true)
    private String eventKey;
    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status;
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;
    @Column(name = "replay_count", nullable = false)
    private Integer replayCount;
    @Column(name = "next_retry_at")
    private Integer nextRetryAt;
    @Column(name = "locked_by", length = 128)
    private String lockedBy;
    @Column(name = "locked_until")
    private Integer lockedUntil;
    @Column(name = "published_at")
    private Integer publishedAt;
    @Column(name = "last_error", length = 512)
    private String lastError;
    @Column(nullable = false)
    private Integer created;
    @Column(nullable = false)
    private Integer updated;
    @Version
    @Column(nullable = false)
    private Long version;
}
