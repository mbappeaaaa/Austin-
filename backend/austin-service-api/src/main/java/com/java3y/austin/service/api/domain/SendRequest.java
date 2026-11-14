package com.java3y.austin.service.api.domain;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 发送/撤回接口的参数
 *
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SendRequest {

    /**
     * 租户身份。HTTP 接口会使用当前登录态覆盖该值，内部调用必须显式传递。
     */
    private Long tenantId;

    /** 客户端发送请求幂等键；Web UI 未提供时由 HTTP 入口生成。 */
    private String idempotencyKey;

    /**
     * 执行业务类型
     *
     * @see com.java3y.austin.service.api.enums.BusinessCode
     * send:发送消息
     * recall:撤回消息
     */
    private String code;

    /**
     * 消息模板Id
     * 【必填】
     */
    private Long messageTemplateId;


    /**
     * 消息相关的参数
     * 当业务类型为"send"，必传
     */
    private MessageParam messageParam;

    /**
     * 需要撤回的消息messageIds (可根据发送接口返回的消息messageId进行撤回)
     * 【可选】
     */
    private List<String> recallMessageIds;

}
