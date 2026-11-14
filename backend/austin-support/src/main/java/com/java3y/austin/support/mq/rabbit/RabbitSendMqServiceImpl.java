package com.java3y.austin.support.mq.rabbit;

import cn.hutool.core.util.IdUtil;
import com.google.common.base.Throwables;
import com.java3y.austin.support.constans.MessageQueuePipeline;
import com.java3y.austin.support.mq.SendMqService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;



/**
 * 【消息链路 - MQ 适配层：RabbitMQ 实现 + Publisher Confirm】
 *
 * 作用：SendMqAction 只依赖 SendMqService 接口，实际发往 Kafka/RabbitMQ/Redis 由配置
 *       austin.mq.pipeline 决定（策略+配置路由，业务代码无感）——"MQ 适配层"的 RabbitMQ 分支。
 *       本类还要回答"消息是否真的到达了 Broker"：靠 Publisher Confirm 给出确定答案。
 *
 * 实现：
 *       1. 每条消息携带 CorrelationData（雪花ID），convertAndSend 发往 austin.point 交换机
 *          （交换机名走配置），按 topic 类型路由到 queues.send / trace / recall 三个队列；
 *       2. Publisher Confirm：Broker 异步回调 future——isAck=true 打 info
 *          "消息成功投递到交换机，消息ID：xxx"（链路日志第3行）；nack/异常打 error，
 *          证明消息没有丢在发送半路；
 *       3. 注意边界：Confirm 只确认"到达交换机"，是否路由进队列可在 RabbitMQ 控制台
 *          看各队列的 Ready/速率。
 *
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "austin.mq.pipeline", havingValue = MessageQueuePipeline.RABBIT_MQ)
public class RabbitSendMqServiceImpl implements SendMqService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${austin.rabbitmq.exchange.name}")
    private String exchangeName;

    @Value("${austin.business.topic.name}")
    private String sendMessageTopic;

    @Value("${austin.business.recall.topic.name}")
    private String austinRecall;

    @Value("${austin.rabbitmq.routing.send}")
    private String sendRoutingKey;

    @Value("${austin.rabbitmq.routing.recall}")
    private String recallRoutingKey;

    @Value("${austin.business.log.topic.name}")
    private String austinTraceLogTopic;

    @Value("${austin.rabbitmq.routing.trace}")
    private String traceRoutingKey;

    @Override
    public void send(String topic, String jsonValue, String tagId) {
        // 【Confirm1】每条消息携带 CorrelationData（雪花ID）用于异步确认；按 topic 匹配 routing key
        // 发往 austin.point 交换机，路由到 queues.send / trace / recall 三个队列
        CorrelationData correlationData = new CorrelationData(IdUtil.getSnowflake().nextIdStr());
        // 【Confirm2】Publisher Confirm：Broker 异步回调——ack 打 info"消息成功投递到交换机"，
        // nack/异常打 error，证明消息没有丢在发送半路（Confirm 只确认到交换机，进没进队列看控制台速率）
        correlationData.getFuture().addCallback(result -> {
            if (result.isAck()) {
                log.info("消息成功投递到交换机，消息ID：{}", correlationData.getId());
            }else{
                log.error("消息投递到交换机失败，消息ID：{}", correlationData.getId());
            }
        }, ex -> {
            log.error("消息处理异常，{}", Throwables.getStackTraceAsString(ex));
        });
        if (topic.equals(sendMessageTopic)){
            rabbitTemplate.convertAndSend(exchangeName, sendRoutingKey, jsonValue, correlationData);
        }else if (topic.equals(austinRecall)){
            rabbitTemplate.convertAndSend(exchangeName, recallRoutingKey, jsonValue, correlationData);
        }else if (topic.equals(austinTraceLogTopic)) {
            rabbitTemplate.convertAndSend(exchangeName, traceRoutingKey, jsonValue, correlationData);
        }else {
            log.error("RabbitSendMqServiceImpl send topic error! topic:{}", topic);
        }
    }

    @Override
    public void send(String topic, String jsonValue) {
        send(topic, jsonValue, null);
    }
}
