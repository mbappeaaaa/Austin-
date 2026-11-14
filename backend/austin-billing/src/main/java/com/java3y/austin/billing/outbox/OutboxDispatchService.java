package com.java3y.austin.billing.outbox;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.domain.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class OutboxDispatchService {
    private final OutboxStateService stateService;
    private final Map<String, OutboxEventHandler> handlers;
    private final String workerId;

    @Autowired
    public OutboxDispatchService(OutboxStateService stateService, List<OutboxEventHandler> handlers) {
        this(stateService, handlers, "billing-outbox-" + UUID.randomUUID().toString());
    }

    OutboxDispatchService(OutboxStateService stateService, List<OutboxEventHandler> handlers,
                          String workerId) {
        this.stateService = stateService;
        this.workerId = workerId;
        this.handlers = new HashMap<>();
        for (OutboxEventHandler handler : handlers) {
            OutboxEventHandler previous = this.handlers.put(handler.eventType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Outbox事件类型存在多个处理器: " + handler.eventType());
            }
        }
    }

    public boolean dispatch(Long id, int leaseSeconds) {
        Optional<OutboxEvent> claimed = stateService.claim(id, workerId, leaseSeconds);
        if (!claimed.isPresent()) {
            return false;
        }
        OutboxEvent event = claimed.get();
        try {
            OutboxEventHandler handler = handlers.get(event.getEventType());
            if (handler == null) {
                throw new IllegalStateException("未注册Outbox事件处理器");
            }
            handler.handle(OutboxMessage.from(event));
            stateService.published(event.getId(), workerId);
            return true;
        } catch (RuntimeException exception) {
            OutboxStatus status = stateService.failed(event.getId(), workerId, exception);
            log.error("Outbox dispatch failed, eventId:{}, eventType:{}, status:{}, failureType:{}",
                    event.getId(), event.getEventType(), status, exception.getClass().getSimpleName());
            return false;
        }
    }
}
