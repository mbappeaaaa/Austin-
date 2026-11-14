package com.java3y.austin.service.api.impl.action.send;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.google.common.base.Throwables;
import com.java3y.austin.billing.service.QuotaService;
import com.java3y.austin.common.domain.SimpleTaskInfo;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.pipeline.BusinessProcess;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.service.api.impl.domain.SendTaskModel;
import com.java3y.austin.support.mq.SendMqService;
import com.java3y.austin.support.service.DashboardCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 【消息链路 - api侧第5站（最后一站）：投递 MQ】（对应日志 [austin链路][2-投递MQ]）
 *
 * 作用：api 侧与 handler 侧的"异步边界"。TaskInfo 序列化成 JSON 投给 MQ 后接口立即返回，
 *       HTTP 请求不等待真实渠道调用——这就是接口"秒回"而消息在后台慢慢发的原因；
 *       同时返回 messageId 列表，作为调用方后续状态追踪的凭据。
 *
 * 实现：
 *       1. JSON.toJSONString(..., WriteClassName)：序列化时携带类名，消费端才能还原各渠道
 *          具体的 ContentModel 子类；
 *       2. 序列化失败 → 释放已预占额度（补偿），终止链路——消息没发出去，额度必须退；
 *       3. sendMqService.send 成功 → 组装 SimpleTaskInfo（messageId/bizId）返回调用方；
 *       4. 投递失败 → 额度置为 unknown 而不是直接释放：消息可能实际已发出（不确定态），
 *          释放会导致超发，交给对账/定时任务兜底——计费"不重不漏"的细节所在。
 *
 */
@Slf4j
@Service
public class SendMqAction implements BusinessProcess<SendTaskModel> {


    @Autowired
    private SendMqService sendMqService;

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private DashboardCounterService dashboardCounterService;

    @Value("${austin.business.topic.name}")
    private String sendMessageTopic;

    @Value("${austin.business.tagId.value}")
    private String tagId;

    @Value("${austin.mq.pipeline}")
    private String mqPipeline;

    @Override
    public void process(ProcessContext<SendTaskModel> context) {
        SendTaskModel sendTaskModel = context.getProcessModel();
        List<TaskInfo> taskInfo = sendTaskModel.getTaskInfo();
        String message;
        try {
            // 【投递1】WriteClassName 序列化携带类名，消费端才能还原各渠道具体的 ContentModel 子类
            message = JSON.toJSONString(sendTaskModel.getTaskInfo(), JSONWriter.Feature.WriteClassName);
        } catch (Exception serializationFailure) {
            // 【投递2】消息没发出去，额度必须退——释放已预占额度并终止链路
            releaseReservations(taskInfo);
            context.setNeedBreak(true).setResponse(BasicResultVO.fail(RespStatusEnum.SERVICE_ERROR));
            log.error("serialize MQ message failed, type:{}",
                    serializationFailure.getClass().getSimpleName());
            return;
        }
        // 【投递3】异步边界：投进 MQ 后接口立即返回，HTTP 请求不等待渠道调用；
        // 响应里的 messageId/bizId 是调用方后续状态追踪的凭据
        try {
            log.info("[austin链路][2-投递MQ] 即将投递 pipeline:{}, topic:{}, task数量:{}", mqPipeline, sendMessageTopic, taskInfo.size());
            sendMqService.send(sendMessageTopic, message, tagId);
            log.info("[austin链路][2-投递MQ] 已发送至交换机, 等待 Publisher Confirm 回调");
            // 【大盘埋点】按接收人数累加"今日提交"，供运营总览展示真实数据
            dashboardCounterService.increment("submit",
                    taskInfo.stream().mapToLong(task -> task.getReceiver().size()).sum());
            // 渠道维度同样记录提交数，支撑"按渠道筛选"视图的完整口径
            taskInfo.forEach(task -> dashboardCounterService.incrementChannel(
                    task.getSendChannel(), "submit", task.getReceiver().size()));
            context.setResponse(BasicResultVO.success(taskInfo.stream().map(v -> SimpleTaskInfo.builder().businessId(v.getBusinessId()).messageId(v.getMessageId()).bizId(v.getBizId()).build()).collect(Collectors.toList())));
        } catch (Exception e) {
            // 【投递4】额度置 unknown 而非直接释放：消息可能实际已发出（不确定态），
            // 释放会导致超发——交给对账/定时任务兜底，计费"不重不漏"
            markReservationsUnknown(taskInfo);
            context.setNeedBreak(true).setResponse(BasicResultVO.fail(RespStatusEnum.SERVICE_ERROR));
            log.error("send {} fail! e:{},params:{}", mqPipeline, Throwables.getStackTraceAsString(e)
                    , JSON.toJSONString(CollUtil.getFirst(taskInfo.listIterator())));
        }
    }

    private void releaseReservations(List<TaskInfo> tasks) {
        for (TaskInfo task : tasks) {
            if (task.getQuotaReservationId() != null) {
                try {
                    quotaService.release(task.getTenantId(), task.getQuotaReservationId());
                } catch (RuntimeException compensationFailure) {
                    log.error("release quota after serialization failure failed, reservationId:{}, type:{}",
                            task.getQuotaReservationId(), compensationFailure.getClass().getSimpleName());
                }
            }
        }
    }

    private void markReservationsUnknown(List<TaskInfo> tasks) {
        for (TaskInfo task : tasks) {
            if (task.getQuotaReservationId() != null) {
                try {
                    quotaService.markUnknown(task.getTenantId(), task.getQuotaReservationId());
                } catch (RuntimeException compensationFailure) {
                    log.error("mark quota unknown after MQ failure failed, reservationId:{}, type:{}",
                            task.getQuotaReservationId(), compensationFailure.getClass().getSimpleName());
                }
            }
        }
    }

}
