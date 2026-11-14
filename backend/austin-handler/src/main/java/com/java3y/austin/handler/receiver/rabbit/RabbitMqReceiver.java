package com.java3y.austin.handler.receiver.rabbit;

import com.alibaba.fastjson2.JSON;
import com.java3y.austin.common.domain.RecallTaskInfo;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.handler.receiver.MessageReceiver;
import com.java3y.austin.handler.receiver.service.ConsumeService;
import com.java3y.austin.support.constans.MessageQueuePipeline;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;


/**
 * 【消息链路 - handler侧第1站：消费接收】（对应日志 [austin链路][3-消费接收]）
 *
 * 作用：MQ 异步解耦后的"对岸"。api 侧投进交换机的消息由本类监听消费，
 *       是 handler 侧责任链（去重→屏蔽→渠道发送）的入口。
 *
 * 实现：
 *       1. @ConditionalOnProperty：仅当 austin.mq.pipeline=rabbitmq 时本类才注册为 Bean，
 *          切换 MQ 实现时这个类根本不加载（MQ 适配层的消费侧）；
 *       2. @RabbitListener 声明 队列+交换机+binding key 三者的绑定关系，
 *          名称全部走配置——RabbitMQ 控制台里 austin.point 交换机的绑定即在此声明；
 *       3. send()：消息体 UTF-8 解码 → JSON.parseArray 反序列化回 List&lt;TaskInfo&gt;
 *          （WriteClassName 写入的类信息在此还原具体 ContentModel 子类）→ 交给
 *          ConsumeService 进入 handler 侧责任链；
 *       4. recall()：撤回消息同理，走 consume2recall 独立链路。
 *
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "austin.mq.pipeline", havingValue = MessageQueuePipeline.RABBIT_MQ)
public class RabbitMqReceiver implements MessageReceiver {

    @Autowired
    private ConsumeService consumeService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${spring.rabbitmq.queues.send}", durable = "true"),
            exchange = @Exchange(value = "${austin.rabbitmq.exchange.name}", type = ExchangeTypes.TOPIC),
            key = "${austin.rabbitmq.routing.send}"
    ))
    public void send(Message message) {
        byte[] body = message.getBody();
        String messageContent = new String(body, StandardCharsets.UTF_8);
        if (StringUtils.isBlank(messageContent)) {
            return;
        }
        // 【消费1】反序列化回 List<TaskInfo>：WriteClassName 写入的类信息在此还原具体 ContentModel 子类
        // 处理发送消息
        List<TaskInfo> taskInfoLists = JSON.parseArray(messageContent, TaskInfo.class);
        log.info("[austin链路][3-消费接收] 从队列收到消息, 解析出 {} 个 TaskInfo", taskInfoLists.size());
        // 【消费2】交给 ConsumeService 进入 handler 侧责任链（去重→屏蔽→渠道发送）
        consumeService.consume2Send(taskInfoLists);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${spring.rabbitmq.queues.recall}", durable = "true"),
            exchange = @Exchange(value = "${austin.rabbitmq.exchange.name}", type = ExchangeTypes.TOPIC),
            key = "${austin.rabbitmq.routing.recall}"
    ))
    public void recall(Message message) {
        byte[] body = message.getBody();
        String messageContent = new String(body, StandardCharsets.UTF_8);
        if (StringUtils.isBlank(messageContent)) {
            return;
        }
        // 【消费3】撤回消息走独立链路 consume2recall
        // 处理撤回消息
        RecallTaskInfo recallTaskInfo = JSON.parseObject(messageContent, RecallTaskInfo.class);
        consumeService.consume2recall(recallTaskInfo);
    }

}
