package com.java3y.austin.handler.inbox;

import com.java3y.austin.handler.pending.Task;
import com.java3y.austin.handler.pending.TaskPendingHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class InboxDispatchService {

    private final InboxService inboxService;
    private final ApplicationContext context;
    private final TaskPendingHolder taskPendingHolder;

    @Value("${austin.mq.inbox.lease-seconds:60}")
    private long leaseSeconds;
    @Value("${austin.mq.inbox.retry-base-seconds:5}")
    private long retryBaseSeconds;

    public InboxDispatchService(InboxService inboxService, ApplicationContext context,
                                TaskPendingHolder taskPendingHolder) {
        this.inboxService = inboxService;
        this.context = context;
        this.taskPendingHolder = taskPendingHolder;
    }

    public boolean dispatch(Long tenantId, Long inboxId, String consumerGroup) {
        String workerId = UUID.randomUUID().toString();
        Optional<InboxMessage> claimed = inboxService.claim(tenantId, inboxId, workerId, leaseSeconds);
        if (!claimed.isPresent()) {
            return false;
        }
        InboxMessage message = claimed.get();
        Task task = context.getBean(Task.class)
                .setInboxId(inboxId)
                .setTenantId(tenantId)
                .setWorkerId(workerId)
                .setPayload(message.getPayload())
                .setRetryBaseSeconds(retryBaseSeconds);
        try {
            taskPendingHolder.route(consumerGroup).execute(task);
            return true;
        } catch (RejectedExecutionException rejected) {
            inboxService.fail(tenantId, inboxId, workerId,
                    "EXECUTOR_REJECTED", "message executor rejected task", retryBaseSeconds);
            log.warn("Inbox dispatch rejected, inboxId:{}, group:{}", inboxId, consumerGroup);
            return false;
        }
    }
}
