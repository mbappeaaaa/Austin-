package com.java3y.austin.billing.outbox;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public class OutboxAdminService {
    private final OutboxStateService stateService;
    private final OutboxDispatchService dispatchService;

    public OutboxAdminService(OutboxStateService stateService,
                              OutboxDispatchService dispatchService) {
        this.stateService = stateService;
        this.dispatchService = dispatchService;
    }

    public Page<OutboxEvent> listDead(Long tenantId, int page, int pageSize) {
        return stateService.listDead(tenantId, page, pageSize);
    }

    public boolean replay(Long tenantId, Long eventId, int leaseSeconds) {
        OutboxEvent event = stateService.replayDead(tenantId, eventId);
        return dispatchService.dispatch(event.getId(), leaseSeconds);
    }
}
