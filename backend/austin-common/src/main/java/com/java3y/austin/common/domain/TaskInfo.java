package com.java3y.austin.common.domain;

import com.java3y.austin.common.dto.model.ContentModel;
import com.java3y.austin.common.pipeline.ProcessModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 发送任务信息
 *
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskInfo implements Serializable, ProcessModel {

    /** 租户身份，必须随 MQ 消息传递。 */
    private Long tenantId;

    /** 额度预占单 ID，随 MQ 任务传递，用于投递结果结算。 */
    private Long quotaReservationId;

    /**
     * 同一业务消息的再次投递标识。普通 MQ 重试保持为空；夜间延迟等重新入队场景使用稳定值，
     * 既不会被首次投递的 Inbox 记录拦截，也能继续拦截延迟队列自身的重复消息。
     */
    private String deliveryAttemptId;

    /**
     * 业务消息发送Id, 用于链路追踪, 若不存在, 则使用 messageId
     */
    private String bizId;

    /**
     * 消息唯一Id(数据追踪使用)
     * 生成逻辑参考 TaskInfoUtils
     */
    private String messageId;

    /**
     * 消息模板Id
     */
    private Long messageTemplateId;

    /**
     * 业务Id(数据追踪使用)
     * 生成逻辑参考 TaskInfoUtils
     */
    private Long businessId;

    /**
     * 接收者
     */
    private Set<String> receiver;

    /**
     * 发送的Id类型
     */
    private Integer idType;

    /**
     * 发送渠道
     */
    private Integer sendChannel;

    /**
     * 模板类型
     */
    private Integer templateType;

    /**
     * 消息类型
     */
    private Integer msgType;

    /**
     * 屏蔽类型
     */
    private Integer shieldType;

    /**
     * 发送文案模型
     * message_template表存储的content是JSON(所有内容都会塞进去)
     * 不同的渠道要发送的内容不一样(比如发push会有img，而短信没有)
     * 所以会有ContentModel
     */
    private ContentModel contentModel;

    /**
     * 发送账号（邮件下可有多个发送账号、短信可有多个发送账号..）
     */
    private Integer sendAccount;


}
