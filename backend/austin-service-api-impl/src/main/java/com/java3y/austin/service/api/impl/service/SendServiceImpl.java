package com.java3y.austin.service.api.impl.service;

import cn.monitor4all.logRecord.annotation.OperationLog;
import com.java3y.austin.common.domain.SimpleTaskInfo;
import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.pipeline.ProcessController;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.service.api.domain.BatchSendRequest;
import com.java3y.austin.service.api.domain.SendRequest;
import com.java3y.austin.service.api.domain.SendResponse;
import com.java3y.austin.service.api.impl.domain.SendTaskModel;
import com.java3y.austin.service.api.service.SendService;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 发送接口
 *
 */
@Service
public class SendServiceImpl implements SendService {

    @Autowired
    @Qualifier("apiProcessController")
    private ProcessController processController;

    @Override
    @OperationLog(bizType = "SendService#send", bizId = "#sendRequest.messageTemplateId", msg = "#sendRequest")
    public SendResponse send(SendRequest sendRequest) {
        //检查sendRequest是否为空
        if (ObjectUtils.isEmpty(sendRequest)) {
            //如果为空，则返回RespStatusEnum中的CLIENT_BAD_PARAMETERS的code(A0001)和msg(客户端参数错误)
            return new SendResponse(RespStatusEnum.CLIENT_BAD_PARAMETERS.getCode(), 
            RespStatusEnum.CLIENT_BAD_PARAMETERS.getMsg(), null);
        }
        //SendTaskModel: 发送任务的消息模型，包含消息模板messageTemplateId、消息参数列表essageParamList、发送任务信息taskInfo
        SendTaskModel sendTaskModel = SendTaskModel.builder()
                .tenantId(sendRequest.getTenantId())
                .idempotencyKey(sendRequest.getIdempotencyKey())
                .messageTemplateId(sendRequest.getMessageTemplateId())
                //singletonList: 返回一个只包含单个元素的不可变 List,固定大小
                .messageParamList(Collections.singletonList(sendRequest.getMessageParam()))
                .build();
       //ProcessContext: 处理上下文，包含处理码code、处理模型processModel、是否中断needBreak、响应response
        ProcessContext context = ProcessContext.builder()
                .code(sendRequest.getCode())
                .processModel(sendTaskModel)
                .needBreak(false)
                .response(BasicResultVO.success()).build();
       // process: 处理上下文 
       // 把构建好的上下文交给流程控制器，由它驱动所有 Action 节点依次跑完，最后返回带着处理结果的上下文
        ProcessContext process = processController.process(context);

        return new SendResponse(process.getResponse().getStatus(), process.getResponse().getMsg(), 
                                (List<SimpleTaskInfo>) process.getResponse().getData());
    }

    @Override
    @OperationLog(bizType = "SendService#batchSend", bizId = "#batchSendRequest.messageTemplateId", msg = "#batchSendRequest")
    public SendResponse batchSend(BatchSendRequest batchSendRequest) {
        if (ObjectUtils.isEmpty(batchSendRequest)) {
            return new SendResponse(RespStatusEnum.CLIENT_BAD_PARAMETERS.getCode(), RespStatusEnum.CLIENT_BAD_PARAMETERS.getMsg(), null);
        }

        SendTaskModel sendTaskModel = SendTaskModel.builder()
                .tenantId(batchSendRequest.getTenantId())
                .idempotencyKey(batchSendRequest.getIdempotencyKey())
                .messageTemplateId(batchSendRequest.getMessageTemplateId())
                .messageParamList(batchSendRequest.getMessageParamList())
                .build();

        ProcessContext context = ProcessContext.builder()
                .code(batchSendRequest.getCode())
                .processModel(sendTaskModel)
                .needBreak(false)
                .response(BasicResultVO.success()).build();

        ProcessContext process = processController.process(context);

        return new SendResponse(process.getResponse().getStatus(), process.getResponse().getMsg(), (List<SimpleTaskInfo>) process.getResponse().getData());
    }


}
