package com.java3y.austin.support.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import com.alibaba.fastjson2.JSON;
import com.google.common.base.Throwables;
import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.domain.AnchorInfo;
import com.java3y.austin.common.domain.SimpleAnchorInfo;
import com.java3y.austin.support.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 全链路埋点统一写库组件。
 * 复刻 Flink 版 AustinSink#realTimeData 的写库逻辑，
 * 供 RabbitMQ / EventBus 等不同消息管道的消费者复用，
 * 保证全链路查询（DataService）读到相同的数据结构。
 *
 */
@Slf4j
@Component
public class AnchorTraceWriter {

    /**
     * messageId / businessId 维度列表保留时长（3天），与 AustinSink 一致
     */
    private static final Long TRACE_DETAIL_TTL_SECONDS = 3 * 24 * 60 * 60L;

    @Autowired
    private RedisUtils redisUtils;

    public void save(AnchorInfo info) {
        if (info == null) {
            return;
        }
        try {
            SimpleAnchorInfo simpleAnchorInfo = SimpleAnchorInfo.builder()
                    .businessId(info.getBusinessId())
                    .state(info.getState())
                    .timestamp(info.getLogTimestamp())
                    .build();
            String simpleJson = JSON.toJSONString(simpleAnchorInfo);

            // 1. messageId 维度（key: Austin:MessageId:{messageId}）
            if (info.getMessageId() != null) {
                String messageKey = CharSequenceUtil.join(StrPool.COLON, AustinConstant.CACHE_KEY_PREFIX,
                        AustinConstant.MESSAGE_ID, info.getMessageId());
                redisUtils.lPush(messageKey, simpleJson, TRACE_DETAIL_TTL_SECONDS);
            }

            // 2. businessId 维度明细索引（key: Austin:BusinessId:{businessId}）
            if (info.getBusinessId() != null) {
                String businessKey = CharSequenceUtil.join(StrPool.COLON, AustinConstant.CACHE_KEY_PREFIX,
                        AustinConstant.BUSINESS_ID, info.getBusinessId());
                redisUtils.lPush(businessKey, simpleJson, TRACE_DETAIL_TTL_SECONDS);
            }

            // 3. userId 维度（key: 用户id，当天有效）
            if (CollUtil.isNotEmpty(info.getIds())) {
                long secondsUntilEndOfDay = (DateUtil.endOfDay(new Date()).getTime() - System.currentTimeMillis()) / 1000;
                for (String id : info.getIds()) {
                    redisUtils.lPush(id, simpleJson, secondsUntilEndOfDay);
                }
            }

            // 4. 消息模板维度（key: businessId 的 hash，保留30天）
            if (info.getBusinessId() != null && CollUtil.isNotEmpty(info.getIds())) {
                Map<String, String> stateCount = new HashMap<>(2);
                stateCount.put(String.valueOf(info.getBusinessId()), String.valueOf(info.getState()));
                long secondsUntil30Days = (DateUtil.offsetDay(new Date(), 30).getTime() - System.currentTimeMillis()) / 1000;
                redisUtils.pipelineHashIncrByEx(stateCount, secondsUntil30Days, (long) info.getIds().size());
            }
        } catch (Exception e) {
            log.error("AnchorTraceWriter#save error! e:{}", Throwables.getStackTraceAsString(e));
        }
    }
}
