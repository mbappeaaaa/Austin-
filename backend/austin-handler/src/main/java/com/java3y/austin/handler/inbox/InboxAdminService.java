package com.java3y.austin.handler.inbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class InboxAdminService {

    private final InboxMessageRepository repository;
    private final InboxService inboxService;
    private final InboxDispatchService dispatchService;

    public InboxAdminService(InboxMessageRepository repository, InboxService inboxService,
                             InboxDispatchService dispatchService) {
        this.repository = repository;
        this.inboxService = inboxService;
        this.dispatchService = dispatchService;
    }

    public Page<InboxMessage> listDead(Long tenantId, int page, int pageSize) {
        requireTenant(tenantId);
        if (page <= 0 || pageSize <= 0 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数无效，每页最多100条");
        }
        return repository.findByTenantIdAndStatusOrderByUpdatedDescIdDesc(
                tenantId, InboxStatus.DEAD, PageRequest.of(page - 1, pageSize));
    }

    public boolean replay(Long tenantId, Long inboxId) {
        InboxMessage message = inboxService.replayDead(tenantId, inboxId);
        return dispatchService.dispatch(tenantId, message.getId(), message.getConsumerGroup());
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
    }
}
