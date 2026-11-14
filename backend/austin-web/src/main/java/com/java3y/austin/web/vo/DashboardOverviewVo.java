package com.java3y.austin.web.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 运营总览聚合数据 VO
 * 数据全部来自真实数据源：额度=billing 表、渠道/模板=support 表、
 * 发送量指标=DashboardCounterService 的 Redis 分桶计数
 */
@Value
@Builder
public class DashboardOverviewVo {

    TenantQuotaVo tenant;
    CountsVo counts;
    MetricsVo metrics;
    List<TrendPointVo> trend;
    List<ChannelHealthVo> channels;

    @Value
    @Builder
    public static class TenantQuotaVo {
        Long tenantId;
        Long availableQuota;
        Long reservedQuota;
        Long warningThreshold;
    }

    @Value
    @Builder
    public static class CountsVo {
        Long templates;
        Integer channelAccounts;
        Integer activeChannels;
    }

    @Value
    @Builder
    public static class MetricsVo {
        Long submitted;
        Long succeeded;
        Long failed;
        Long filtered;
        Long unknown;
        Double successRate;
    }

    @Value
    @Builder
    public static class TrendPointVo {
        String label;
        Long submitted;
        Long succeeded;
        Long failed;
    }

    @Value
    @Builder
    public static class ChannelHealthVo {
        Integer channelCode;
        String channelName;
        Integer accountCount;
        Long total;
        Double successRate;
        String state;
    }
}
