package com.java3y.austin.stream.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

/**
 * 消息队列工具类
 *
 */
@Slf4j
public class MessageQueueUtils {
    private MessageQueueUtils() {
    }

    /**
     * 获取kafkaConsumer
     *
     * @param topicName
     * @param groupId
     * @return
     */
    public static KafkaSource<String> getKafkaConsumer(String topicName, String groupId, String broker) {
        return KafkaSource.<String>builder()
                .setBootstrapServers(broker)
                .setTopics(topicName)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
    }
}
