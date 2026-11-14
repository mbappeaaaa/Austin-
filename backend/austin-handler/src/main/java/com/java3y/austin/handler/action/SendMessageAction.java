package com.java3y.austin.handler.action;

import cn.hutool.core.util.ObjectUtil;
import com.google.common.collect.Sets;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.common.pipeline.BusinessProcess;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.handler.delivery.QuotaDeliveryService;
import com.java3y.austin.handler.handler.DeliveryResult;
import com.java3y.austin.handler.handler.DeliveryStatus;
import com.java3y.austin.handler.handler.Handler;
import com.java3y.austin.handler.handler.HandlerHolder;
import com.java3y.austin.support.service.DashboardCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 【消息链路 - handler侧第4站（最后一站）：渠道发送】（对应日志 [austin链路][6-渠道发送]）
 *
 * 作用：去重/屏蔽都放行后，把 TaskInfo 交给目标渠道的 Handler 真正调第三方接口或写站内信表。
 *       这是"策略模式+工厂路由"的执行点：新增渠道只需新增 Handler 注册进工厂，本类零改动。
 *
 * 实现：
 *       1. HandlerHolder.route(sendChannel) 按 ChannelType 路由到对应 Handler（工厂），
 *          Handler 内部再按渠道凭据（SendAccount）调用各自 SDK/HTTP 接口；
 *       2. 微信小程序&服务号渠道只支持单发：克隆 TaskInfo 逐个 receiver 调 handler；
 *          其余渠道（短信/邮件/webhook 等）整批一次投递；
 *       3. 幂等保护：isAlreadyCompleted(operationKey) 判断该批接收者是否已投递过，
 *          防止 MQ 重复消费导致重复发送（配合 Inbox 表幂等的第二道防线）；
 *       4. 额度结算：投递完成调 complete 按 DeliveryResult 结算已发送份数（三阶段之"结算"），
 *          releaseRemaining 把剩余份数释放退回（三阶段之"释放"）——计费闭环在这里完成。
 *
 */
@Slf4j
@Service
public class SendMessageAction implements BusinessProcess<TaskInfo> {
    @Autowired
    private HandlerHolder handlerHolder;
    @Autowired
    private QuotaDeliveryService quotaDeliveryService;
    @Autowired
    private DashboardCounterService dashboardCounterService;

    @Override
    public void process(ProcessContext<TaskInfo> context) {
        TaskInfo taskInfo = context.getProcessModel();

        // 【发送1】小程序&服务号只支持单发：克隆 TaskInfo 逐个 receiver 投递
        // 微信小程序&服务号只支持单人推送，为了后续逻辑统一处理，于是在这做了单发处理
        if (ChannelType.MINI_PROGRAM.getCode().equals(taskInfo.getSendChannel())
                || ChannelType.OFFICIAL_ACCOUNT.getCode().equals(taskInfo.getSendChannel())
                || ChannelType.ALIPAY_MINI_PROGRAM.getCode().equals(taskInfo.getSendChannel())) {
            Handler handler = handlerHolder.route(taskInfo.getSendChannel());
            for (String receiver : taskInfo.getReceiver()) {
                String operationKey = quotaDeliveryService.receiverOperationKey(taskInfo, receiver);
                if (quotaDeliveryService.isAlreadyCompleted(taskInfo, operationKey)) {
                    continue;
                }
                TaskInfo taskClone = ObjectUtil.cloneByStream(taskInfo);
                taskClone.setReceiver(Sets.newHashSet(receiver));
                DeliveryResult result = handler.doHandler(taskClone);
                quotaDeliveryService.complete(taskInfo, 1L, operationKey, result);
            }
            quotaDeliveryService.releaseRemaining(taskInfo, "PIPELINE_FILTERED");
            return;
        }
        String operationKey = quotaDeliveryService.batchOperationKey(taskInfo, taskInfo.getReceiver());
        // 【发送2】幂等保护：该批接收者已投递过则跳过，防 MQ 重复消费导致重复发送
        if (!quotaDeliveryService.isAlreadyCompleted(taskInfo, operationKey)) {
            // 【发送3】按 ChannelType 工厂路由到对应 Handler——"新增渠道零侵入"的执行点
            log.info("[austin链路][6-渠道发送] 路由到渠道:{}, 接收人数:{}", taskInfo.getSendChannel(), taskInfo.getReceiver().size());
            DeliveryResult result = handlerHolder.route(taskInfo.getSendChannel()).doHandler(taskInfo);
            log.info("[austin链路][6-渠道发送] 渠道投递完成, result:{}", result);
            // 【大盘埋点】按投递结果累加 成功/失败/未知，并按渠道分桶（渠道健康度）
            countByResult(taskInfo, result);
            // 【发送4】按投递结果结算已发送份数（三阶段之"结算"）
            quotaDeliveryService.complete(taskInfo, taskInfo.getReceiver().size(), operationKey, result);
        }
        // 剩余份数释放退回（三阶段之"释放"）——计费闭环在这里完成
        quotaDeliveryService.releaseRemaining(taskInfo, "PIPELINE_FILTERED");
    }

    /**
     * 大盘埋点：按 DeliveryResult 状态累加全局计数与渠道维度计数
     */
    private void countByResult(TaskInfo taskInfo, DeliveryResult result) {
        if (result == null) {
            return;
        }
        long receivers = taskInfo.getReceiver().size();
        DeliveryStatus status = result.getStatus();
        if (DeliveryStatus.SUCCESS.equals(status)) {
            dashboardCounterService.increment("success", receivers);
            dashboardCounterService.incrementChannel(taskInfo.getSendChannel(), "success", receivers);
        } else if (DeliveryStatus.FINAL_FAILURE.equals(status) || DeliveryStatus.RETRYABLE_FAILURE.equals(status)) {
            dashboardCounterService.increment("fail", receivers);
            dashboardCounterService.incrementChannel(taskInfo.getSendChannel(), "fail", receivers);
        } else if (DeliveryStatus.UNKNOWN.equals(status)) {
            dashboardCounterService.increment("unknown", receivers);
            dashboardCounterService.incrementChannel(taskInfo.getSendChannel(), "unknown", receivers);
        }
    }
}
