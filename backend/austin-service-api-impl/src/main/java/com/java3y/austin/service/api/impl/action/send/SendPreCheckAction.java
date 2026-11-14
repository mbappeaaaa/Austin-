package com.java3y.austin.service.api.impl.action.send;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.pipeline.BusinessProcess;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.service.api.domain.MessageParam;
import com.java3y.austin.service.api.impl.domain.SendTaskModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 【消息链路 - api侧第1站：参数预检】（链路图"参数预检/白名单"节点）
 *
 * 作用：消息发送的第一道闸门。用户点"测试发送"或业务方调 /message/send 后，
 *       先把明显不合法的请求拦下来快速失败，避免无效请求进入后续链路浪费 MQ 和渠道资源；
 *       被拦截时设置 needBreak，责任链后续节点不再执行。
 *
 * 实现：纯内存校验、不查库，保证接口快速返回：
 *       1. 校验租户ID、模板ID、参数列表是否存在；
 *       2. 过滤 receiver 为空白的 MessageParam，过滤后为空直接返回错误；
 *       3. 单个请求接收者数超过 BATCH_RECEIVER_SIZE(100) 拒绝，防止一次请求压垮下游。
 */
@Slf4j
@Service
public class SendPreCheckAction implements BusinessProcess<SendTaskModel> {

    @Override
    public void process(ProcessContext<SendTaskModel> context) {
        SendTaskModel sendTaskModel = context.getProcessModel();

        Long messageTemplateId = sendTaskModel.getMessageTemplateId();
        List<MessageParam> messageParamList = sendTaskModel.getMessageParamList();

        // 【预检1】基础参数存在性校验——任一为空立即快速失败，责任链后续节点不再执行
        // 1. 没有传入 消息模板Id 或者 messageParam
        if (Objects.isNull(sendTaskModel.getTenantId()) || Objects.isNull(messageTemplateId)
                || CollUtil.isEmpty(messageParamList)) {
            context.setNeedBreak(true).setResponse(BasicResultVO.fail(RespStatusEnum.CLIENT_BAD_PARAMETERS,
                    "租户ID、模板ID或参数列表为空"));
            return;
        }

        // 【预检2】过滤掉 receiver 为空白的 MessageParam，过滤后为空直接返回错误
        // 2. 过滤 receiver=null 的messageParam
        List<MessageParam> resultMessageParamList = messageParamList.stream()
                .filter(messageParam -> !CharSequenceUtil.isBlank(messageParam.getReceiver()))
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(resultMessageParamList)) {
            context.setNeedBreak(true).setResponse(BasicResultVO.fail(RespStatusEnum.CLIENT_BAD_PARAMETERS, "含接受者的参数列表为空"));
            return;
        }

        // 【预检3】单次请求接收者数超过 BATCH_RECEIVER_SIZE(100) 拒绝，防止一次请求压垮下游
        // 3. 过滤 receiver 大于100的请求
        if (resultMessageParamList.stream().anyMatch(messageParam -> messageParam.getReceiver().split(StrPool.COMMA).length > AustinConstant.BATCH_RECEIVER_SIZE)) {
            context.setNeedBreak(true).setResponse(BasicResultVO.fail(RespStatusEnum.TOO_MANY_RECEIVER));
            return;
        }

        sendTaskModel.setMessageParamList(resultMessageParamList);

    }
}
