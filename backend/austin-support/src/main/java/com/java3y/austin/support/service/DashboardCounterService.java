package com.java3y.austin.support.service;

import cn.hutool.core.date.DateUtil;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

/**
 * 运营总览实时计数器（Redis 分桶计数）
 *
 * 作用：为运营总览大盘提供"全局发送量"的真实数据源。austin 原生的埋点聚合
 *       （trace → Flink → Redis）只有 messageId/userId/businessId 维度，
 *       没有全局按时间分桶的统计结构，本组件补齐这一层。
 *
 * 实现：
 *       1. 四个指标：submit（提交进MQ）/ success（渠道投递成功）/
 *          fail（渠道投递失败）/ filtered（去重/屏蔽拦截）；
 *       2. 每次计数同时写两个桶：小时桶（趋势图"今日"视图，48h 过期）+
 *          天桶（"7天/30天"视图，32 天过期）；渠道维度另有按渠道的天桶（8 天过期）；
 *       3. key 带日期自滚动，过期由 TTL 保证回收，无需清理任务；
 *       4. 计数失败只记日志不抛异常——统计是旁路逻辑，不能影响主发送链路。
 */
@Component
@Slf4j
public class DashboardCounterService {

    private static final String KEY_PREFIX = "austin:dashboard:";
    private static final Duration HOUR_TTL = Duration.ofHours(48);
    private static final Duration DAY_TTL = Duration.ofDays(32);

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 全局指标计数：同时累加小时桶与天桶
     *
     * @param metric submit / success / fail / filtered / unknown
     * @param delta  本次数量（通常为接收人数）
     */
    public void increment(String metric, long delta) {
        if (delta <= 0) {
            return;
        }
        Date now = new Date();
        incr(KEY_PREFIX + "hour:" + metric + ":" + DateUtil.format(now, "yyyyMMddHH"), delta, HOUR_TTL);
        incr(KEY_PREFIX + "day:" + metric + ":" + DateUtil.format(now, "yyyyMMdd"), delta, DAY_TTL);
    }

    /**
     * 渠道维度计数：同时写小时桶与天桶，支持"按渠道筛选"的趋势/指标展示
     */
    public void incrementChannel(Integer channelCode, String metric, long delta) {
        if (delta <= 0 || channelCode == null) {
            return;
        }
        Date now = new Date();
        incr(KEY_PREFIX + "ch:" + channelCode + ":hour:" + metric + ":" + DateUtil.format(now, "yyyyMMddHH"), delta, HOUR_TTL);
        incr(KEY_PREFIX + "ch:" + channelCode + ":day:" + metric + ":" + DateUtil.format(now, "yyyyMMdd"), delta, DAY_TTL);
    }

    public Long readHour(String metric, String yyyyMMddHH) {
        return read(KEY_PREFIX + "hour:" + metric + ":" + yyyyMMddHH);
    }

    public Long readDay(String metric, String yyyyMMdd) {
        return read(KEY_PREFIX + "day:" + metric + ":" + yyyyMMdd);
    }

    public Long readChannelHour(Integer channelCode, String metric, String yyyyMMddHH) {
        return read(KEY_PREFIX + "ch:" + channelCode + ":hour:" + metric + ":" + yyyyMMddHH);
    }

    public Long readChannelDay(Integer channelCode, String metric, String yyyyMMdd) {
        return read(KEY_PREFIX + "ch:" + channelCode + ":day:" + metric + ":" + yyyyMMdd);
    }

    private void incr(String key, long delta, Duration ttl) {
        try {
            redisTemplate.opsForValue().increment(key, delta);
            redisTemplate.expire(key, ttl);
        } catch (Exception e) {
            log.error("DashboardCounterService#incr fail, key:{}, e:{}", key, Throwables.getStackTraceAsString(e));
        }
    }

    private Long read(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            log.error("DashboardCounterService#read fail, key:{}, e:{}", key, Throwables.getStackTraceAsString(e));
            return 0L;
        }
    }
}
