package com.java3y.austin.service.api.impl.domain;

import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.pipeline.ProcessModel;
import com.java3y.austin.service.api.domain.MessageParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @description 发送消息任务模型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SendTaskModel implements ProcessModel {

    private Long tenantId;
    /**
     * 幂等键
     */
    private String idempotencyKey;

    /**
     * 消息模板Id
     */
    private Long messageTemplateId;

    /**
     * 请求参数
     */
    private List<MessageParam> messageParamList;

    /**
     * 发送任务的信息
     */
    private List<TaskInfo> taskInfo;

}
