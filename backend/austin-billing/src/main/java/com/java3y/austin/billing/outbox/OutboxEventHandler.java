package com.java3y.austin.billing.outbox;

/**
 * Outbox 业务事件处理 SPI。处理器必须使用 eventKey 实现业务侧幂等。
 */
public interface OutboxEventHandler {
    String eventType();

    void handle(OutboxMessage message);
}
