package com.java3y.austin.web.vo;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxDeadLetterVo {
    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String eventKey;
    private Integer attemptCount;
    private Integer replayCount;
    private String lastError;
    private Integer created;
    private Integer updated;

    public static OutboxDeadLetterVo from(OutboxEvent event) {
        return OutboxDeadLetterVo.builder().id(event.getId())
                .aggregateType(event.getAggregateType()).aggregateId(event.getAggregateId())
                .eventType(event.getEventType()).eventKey(event.getEventKey())
                .attemptCount(event.getAttemptCount()).replayCount(event.getReplayCount())
                .lastError(event.getLastError()).created(event.getCreated())
                .updated(event.getUpdated()).build();
    }
}
