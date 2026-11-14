package com.java3y.austin.billing.outbox;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 传给业务处理器的不可持久化事件快照，避免处理器修改 Outbox 实体状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage {
    private Long id;
    private Long tenantId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String eventKey;
    private String payload;

    public static OutboxMessage from(OutboxEvent event) {
        return OutboxMessage.builder().id(event.getId()).tenantId(event.getTenantId())
                .aggregateType(event.getAggregateType()).aggregateId(event.getAggregateId())
                .eventType(event.getEventType()).eventKey(event.getEventKey())
                .payload(event.getPayload()).build();
    }
}
