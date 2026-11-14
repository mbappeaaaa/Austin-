package com.java3y.austin.handler.action;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.java3y.austin.common.domain.AnchorInfo;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.AnchorState;
import com.java3y.austin.common.enums.ShieldType;
import com.java3y.austin.common.pipeline.BusinessProcess;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.handler.delivery.QuotaDeliveryService;
import com.java3y.austin.support.utils.LogUtils;
import com.java3y.austin.support.utils.RedisUtils;
import com.java3y.austin.support.service.DashboardCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;


/**
 * 【消息链路 - handler侧第3站：夜间屏蔽】（对应日志 [austin链路][5-屏蔽]）
 *
 * 作用：运营可给模板配置屏蔽策略，避免凌晨打扰用户——营销消息半夜发出去只会招投诉。
 *
 * 实现：
 *       1. shieldType=10（不屏蔽）直接 return，链路继续；新建模板时后端默认补 10；
 *       2. 凌晨 0~8 点（hour &lt; 8）进入屏蔽分支：
 *          - shieldType=20：直接拦截，打 NIGHT_SHIELD 埋点、释放剩余额度、终止链路；
 *          - shieldType=30："拦截但延迟投递"——TaskInfo 序列化推入 Redis List
 *            （key=night_shield_send，1天过期），由定时任务次日早上取出重发；
 *       3. 当前时间通过 Clock 注入获取，单测时可 mock 时间验证分支，不必真等到凌晨。
 *
 */
@Slf4j
@Service
public class ShieldAction implements BusinessProcess<TaskInfo> {

    private static final String NIGHT_SHIELD_BUT_NEXT_DAY_SEND_KEY = "night_shield_send";
    private static final long SECONDS_OF_A_DAY = 86400L;

    /**
     * 默认早上8点之前是凌晨
     */
    private static final int NIGHT = 8;

    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private LogUtils logUtils;
    @Autowired
    private QuotaDeliveryService quotaDeliveryService;
    @Autowired
    private DashboardCounterService dashboardCounterService;
    private Clock clock = Clock.systemDefaultZone();

    ShieldAction() {
    }

    ShieldAction(Clock clock) {
        this.clock = clock;
    }


    @Override
    public void process(ProcessContext<TaskInfo> context) {
        TaskInfo taskInfo = context.getProcessModel();

        log.info("[austin链路][5-屏蔽] shieldType:{} (10=不屏蔽 20=夜间屏蔽 30=夜间屏蔽次日发送)", taskInfo.getShieldType());
        // 【屏蔽1】10=不屏蔽直接放行（新建模板时后端默认补 10）
        if (ShieldType.NIGHT_NO_SHIELD.getCode().equals(taskInfo.getShieldType())) {
            return;
        }

        // 【屏蔽2】凌晨 0~8 点（hour < 8）进入屏蔽分支
        if (LocalDateTime.now(clock).getHour() < NIGHT) {
            // 20=直接拦截：打埋点、释放剩余额度、终止链路
            if (ShieldType.NIGHT_SHIELD.getCode().equals(taskInfo.getShieldType())) {
                // 【大盘埋点】夜间硬拦截计入"被屏蔽"
                dashboardCounterService.increment("filtered", taskInfo.getReceiver().size());
                logUtils.print(AnchorInfo.builder().state(AnchorState.NIGHT_SHIELD.getCode())
                        .bizId(taskInfo.getBizId()).messageId(taskInfo.getMessageId()).businessId(taskInfo.getBusinessId()).ids(taskInfo.getReceiver()).build());
                quotaDeliveryService.releaseRemaining(taskInfo, "NIGHT_SHIELDED");
            }
            // 30=拦截但延迟投递：TaskInfo 推入 Redis List（1天过期），定时任务次日早上取出重发
            if (ShieldType.NIGHT_SHIELD_BUT_NEXT_DAY_SEND.getCode().equals(taskInfo.getShieldType())) {
                String messageIdentity = CharSequenceUtil.isNotBlank(taskInfo.getMessageId())
                        ? taskInfo.getMessageId() : (CharSequenceUtil.isNotBlank(taskInfo.getBizId())
                        ? taskInfo.getBizId() : String.valueOf(taskInfo.getBusinessId()));
                taskInfo.setDeliveryAttemptId("night:" + DigestUtil.sha256Hex(
                        taskInfo.getTenantId() + "|" + messageIdentity));
                taskInfo.setShieldType(ShieldType.NIGHT_NO_SHIELD.getCode());
                redisUtils.lPush(NIGHT_SHIELD_BUT_NEXT_DAY_SEND_KEY, JSON.toJSONString(taskInfo,
                                JSONWriter.Feature.WriteClassName),
                        SECONDS_OF_A_DAY);
                logUtils.print(AnchorInfo.builder().state(AnchorState.NIGHT_SHIELD_NEXT_SEND.getCode()).bizId(taskInfo.getBizId()).messageId(taskInfo.getMessageId()).businessId(taskInfo.getBusinessId()).ids(taskInfo.getReceiver()).build());
            }
            context.setNeedBreak(true);
        }

    }
}
