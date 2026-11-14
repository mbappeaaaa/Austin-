package com.java3y.austin.handler.action;

import cn.hutool.core.collection.CollUtil;
import com.java3y.austin.common.constant.CommonConstant;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.DeduplicationType;
import com.java3y.austin.common.enums.EnumUtil;
import com.java3y.austin.common.pipeline.BusinessProcess;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.handler.deduplication.DeduplicationHolder;
import com.java3y.austin.handler.deduplication.DeduplicationParam;
import com.java3y.austin.handler.delivery.QuotaDeliveryService;
import com.java3y.austin.support.service.ConfigService;
import com.java3y.austin.support.service.DashboardCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;


/**
 * 【消息链路 - handler侧第2站：去重】（对应日志 [austin链路][4-去重]）
 *
 * 作用：消费侧治理第一环，把"不该重复发的消息"拦下来——运营重复点发送、业务方超时重试，
 *       到了这里都会被识别；接收者被剔光后整条链路终止并释放额度，避免用户被重复打扰。
 *
 * 实现：
 *       1. 去重规则来自配置 deduplicationRule（样例：{"deduplication_10":{"num":1,"time":300},
 *          "deduplication_20":{"num":5}}），10=内容去重（相同内容N分钟内只发一条）、
 *          20=频次去重（同一渠道同一接收者一天最多N条），规则热更新不用改代码；
 *       2. 责任链里套策略：DeduplicationHolder 按去重类型选择对应 Builder（规则+TaskInfo
 *          组装成参数）与 Service（真正执行），底层基于 Redis——内容去重用 ZSET+Lua
 *          滑动窗口（"判断是否超限"与"写入记录"两步由 Lua 脚本原子完成，防并发穿透），
 *          频次去重按渠道+接收者维度计数；
 *       3. 去重后 receiver 为空 → releaseRemaining 释放剩余额度（只有真正发出去的才结算），
 *          终止链路——三阶段里"释放"的触发点之一。
 *
 */
@Slf4j
@Service
public class DeduplicationAction implements BusinessProcess<TaskInfo> {

    public static final String DEDUPLICATION_RULE_KEY = "deduplicationRule";

    @Autowired
    private ConfigService config;

    @Autowired
    private DeduplicationHolder deduplicationHolder;

    @Autowired
    private QuotaDeliveryService quotaDeliveryService;

    @Autowired
    private DashboardCounterService dashboardCounterService;

    @Override
    public void process(ProcessContext<TaskInfo> context) {
        TaskInfo taskInfo = context.getProcessModel();

        // 【大盘埋点】记录去重前人数，结束后按差值累加"被拦截"数量
        int receiverCountBefore = taskInfo.getReceiver().size();

        // 【去重1】规则来自配置 deduplicationRule，10=内容去重、20=频次去重，热更新不用改代码
        // 配置样例{"deduplication_10":{"num":1,"time":300},"deduplication_20":{"num":5}}
        String deduplicationConfig = config.getProperty(DEDUPLICATION_RULE_KEY, CommonConstant.EMPTY_JSON_OBJECT);

        // 【去重2】Holder 按去重类型选择 Builder+Service 执行：内容去重=Redis ZSET+Lua 滑动窗口
        // （"判断超限"与"写入记录"由 Lua 原子完成，防并发穿透），频次去重=渠道+接收者维度计数
        // 去重
        log.info("[austin链路][4-去重] 执行去重规则, messageId:{}, 接收人数:{}", taskInfo.getMessageId(), taskInfo.getReceiver().size());
        List<Integer> deduplicationList = EnumUtil.getCodeList(DeduplicationType.class);
        for (Integer deduplicationType : deduplicationList) {
            DeduplicationParam deduplicationParam = deduplicationHolder.selectBuilder(deduplicationType).build(deduplicationConfig, taskInfo);
            if (Objects.nonNull(deduplicationParam)) {
                deduplicationHolder.selectService(deduplicationType).deduplication(deduplicationParam);
            }
        }

        // 【去重3】接收者全部被拦 → 释放剩余额度（只结算真正发出的），终止链路
        int filteredCount = receiverCountBefore - taskInfo.getReceiver().size();
        if (filteredCount > 0) {
            dashboardCounterService.increment("filtered", filteredCount);
        }
        if (CollUtil.isEmpty(taskInfo.getReceiver())) {
            log.info("[austin链路][4-去重] 全部接收者被去重拦截, 终止链路并释放额度");
            quotaDeliveryService.releaseRemaining(taskInfo, "ALL_RECEIVERS_DEDUPLICATED");
            context.setNeedBreak(true);
        }
    }
}
