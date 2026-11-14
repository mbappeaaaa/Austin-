package com.java3y.austin.handler.inbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class InboxRetryScheduler {

    private final InboxMessageRepository repository;
    private final InboxDispatchService dispatchService;

    @Value("${austin.mq.inbox.scan-batch-size:100}")
    private int scanBatchSize;

    public InboxRetryScheduler(InboxMessageRepository repository, InboxDispatchService dispatchService) {
        this.repository = repository;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${austin.mq.inbox.scan-interval-ms:5000}")
    public void dispatchReadyMessages() {
        int batchSize = Math.max(1, Math.min(scanBatchSize, 1000));
        List<InboxMessage> messages = repository.findReady(InboxStatus.PENDING,
                InboxStatus.RETRY_WAIT, InboxStatus.PROCESSING, Instant.now().getEpochSecond(),
                PageRequest.of(0, batchSize));
        for (InboxMessage message : messages) {
            try {
                dispatchService.dispatch(message.getTenantId(), message.getId(), message.getConsumerGroup());
            } catch (RuntimeException exception) {
                log.error("Inbox retry dispatch failed, inboxId:{}, type:{}",
                        message.getId(), exception.getClass().getSimpleName());
            }
        }
    }
}
