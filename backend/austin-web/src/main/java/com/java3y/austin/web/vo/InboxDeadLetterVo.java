package com.java3y.austin.web.vo;

import com.java3y.austin.handler.inbox.InboxMessage;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InboxDeadLetterVo {
    Long id;
    String consumerGroup;
    String consumeKey;
    Integer attemptCount;
    Integer maxAttempts;
    Integer replayCount;
    String lastErrorCode;
    String lastErrorMessage;
    Integer created;
    Integer updated;

    public static InboxDeadLetterVo from(InboxMessage message) {
        return InboxDeadLetterVo.builder().id(message.getId())
                .consumerGroup(message.getConsumerGroup()).consumeKey(message.getConsumeKey())
                .attemptCount(message.getAttemptCount()).maxAttempts(message.getMaxAttempts())
                .replayCount(message.getReplayCount()).lastErrorCode(message.getLastErrorCode())
                .lastErrorMessage(message.getLastErrorMessage()).created(message.getCreated())
                .updated(message.getUpdated()).build();
    }
}
