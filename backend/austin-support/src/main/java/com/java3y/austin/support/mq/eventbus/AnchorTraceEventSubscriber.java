package com.java3y.austin.support.mq.eventbus;

import com.google.common.eventbus.Subscribe;
import com.java3y.austin.common.domain.AnchorInfo;
import com.java3y.austin.support.constans.MessageQueuePipeline;
import com.java3y.austin.support.service.AnchorTraceWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * EventBus 管道的全链路埋点消费者。
 * 消费 LogUtils 发出的 AnchorInfo 埋点，写入 Redis，
 * 保证本地开发（eventBus 管道）下全链路查询可用，无需 Kafka/Flink。
 *
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "austin.mq.pipeline", havingValue = MessageQueuePipeline.EVENT_BUS)
public class AnchorTraceEventSubscriber {

    @Autowired
    private AnchorTraceWriter anchorTraceWriter;

    @Subscribe
    public void consume(AnchorInfo anchorInfo) {
        anchorTraceWriter.save(anchorInfo);
    }
}
