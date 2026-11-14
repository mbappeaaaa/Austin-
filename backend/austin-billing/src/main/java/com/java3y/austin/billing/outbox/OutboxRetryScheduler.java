package com.java3y.austin.billing.outbox;

import com.java3y.austin.billing.payment.domain.OutboxStatus;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "austin.billing.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetryScheduler {
    private final OutboxEventRepository repository;
    private final OutboxDispatchService dispatchService;

    @Value("${austin.billing.outbox.scan-batch-size:100}")
    private int scanBatchSize;
    @Value("${austin.billing.outbox.lease-seconds:60}")
    private int leaseSeconds;

    public OutboxRetryScheduler(OutboxEventRepository repository,
                                OutboxDispatchService dispatchService) {
        this.repository = repository;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${austin.billing.outbox.scan-interval-ms:5000}")
    public void dispatchReadyEvents() {
        int batchSize = Math.max(1, Math.min(scanBatchSize, 1000));
        List<Long> ids = repository.findReadyIds(OutboxStatus.PENDING, OutboxStatus.RETRY_WAIT,
                OutboxStatus.PROCESSING, Math.toIntExact(Instant.now().getEpochSecond()),
                PageRequest.of(0, batchSize));
        for (Long id : ids) {
            try {
                dispatchService.dispatch(id, leaseSeconds);
            } catch (RuntimeException exception) {
                log.error("Outbox scheduler failed, eventId:{}, failureType:{}",
                        id, exception.getClass().getSimpleName());
            }
        }
    }
}
