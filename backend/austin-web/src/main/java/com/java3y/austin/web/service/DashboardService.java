package com.java3y.austin.web.service;

import cn.hutool.core.date.DateUtil;
import com.java3y.austin.billing.domain.QuotaAccount;
import com.java3y.austin.billing.service.QuotaService;
import com.java3y.austin.common.constant.CommonConstant;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.support.dao.ChannelAccountDao;
import com.java3y.austin.support.dao.MessageTemplateDao;
import com.java3y.austin.support.domain.ChannelAccount;
import com.java3y.austin.support.service.DashboardCounterService;
import com.java3y.austin.web.vo.DashboardOverviewVo;
import com.java3y.austin.web.vo.DashboardOverviewVo.ChannelHealthVo;
import com.java3y.austin.web.vo.DashboardOverviewVo.CountsVo;
import com.java3y.austin.web.vo.DashboardOverviewVo.MetricsVo;
import com.java3y.austin.web.vo.DashboardOverviewVo.TenantQuotaVo;
import com.java3y.austin.web.vo.DashboardOverviewVo.TrendPointVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 运营总览聚合服务：把额度、渠道账号、模板、Redis 发送量计数
 * 聚合成前端 Dashboard 一口价接口的数据
 */
@Service
@Slf4j
public class DashboardService {

    @Autowired
    private QuotaService quotaService;
    @Autowired
    private ChannelAccountDao channelAccountDao;
    @Autowired
    private MessageTemplateDao messageTemplateDao;
    @Autowired
    private DashboardCounterService dashboardCounterService;

    /**
     * @param tenantId    当前登录租户
     * @param range       today（24 小时桶）/ 7d / 30d（天桶）
     * @param channelCode 为空时返回全渠道聚合；指定时指标/趋势/健康度按该渠道分桶数据计算
     */
    public DashboardOverviewVo overview(Long tenantId, String range, Integer channelCode) {
        boolean byDay = !"today".equals(range);
        int buckets = "7d".equals(range) ? 7 : ("30d".equals(range) ? 30 : 24);
        Date now = new Date();

        // 1. 时间桶趋势 + 指标汇总（全局桶或渠道桶）
        List<TrendPointVo> trend = new ArrayList<>(buckets);
        long submitted = 0L;
        long succeeded = 0L;
        long failed = 0L;
        for (int i = buckets - 1; i >= 0; i--) {
            Date point = byDay ? DateUtil.offsetDay(now, -i) : DateUtil.offsetHour(now, -i);
            String label = byDay ? DateUtil.format(point, "MM-dd") : DateUtil.format(point, "HH") + "时";
            String bucket = byDay ? DateUtil.format(point, "yyyyMMdd") : DateUtil.format(point, "yyyyMMddHH");
            long s = readMetric("submit", byDay, bucket, channelCode);
            long ok = readMetric("success", byDay, bucket, channelCode);
            long bad = readMetric("fail", byDay, bucket, channelCode);
            submitted += s;
            succeeded += ok;
            failed += bad;
            trend.add(TrendPointVo.builder().label(label).submitted(s).succeeded(ok).failed(bad).build());
        }

        // 2. 拦截/未知指标（单独聚合，口径与趋势一致；渠道维度暂不记录拦截）
        long filtered = 0L;
        long unknown = 0L;
        for (int i = 0; i < buckets; i++) {
            Date point = byDay ? DateUtil.offsetDay(now, -i) : DateUtil.offsetHour(now, -i);
            String bucket = byDay ? DateUtil.format(point, "yyyyMMdd") : DateUtil.format(point, "yyyyMMddHH");
            unknown += readMetric("unknown", byDay, bucket, channelCode);
            if (channelCode == null) {
                filtered += readMetric("filtered", byDay, bucket, channelCode);
            }
        }

        // 3. 额度账户（真实计费数据）
        QuotaAccount account = quotaService.requireAccount(tenantId);

        // 4. 渠道账号 / 模板统计
        // allAccounts 始终为全量（渠道健康列表/下拉选项需要展示所有渠道）；
        // accounts 在指定渠道时被过滤，仅用于 counts 统计
        List<ChannelAccount> allAccounts = channelAccountDao
                .findAllByTenantIdAndIsDeletedEquals(tenantId, CommonConstant.FALSE);
        List<ChannelAccount> accounts = allAccounts;
        if (channelCode != null) {
            accounts = allAccounts.stream()
                    .filter(item -> channelCode.equals(item.getSendChannel()))
                    .collect(Collectors.toList());
        }
        Long templateCount = messageTemplateDao.countByTenantIdAndIsDeletedEquals(tenantId, CommonConstant.FALSE);

        // 5. 渠道健康：按渠道聚合今日成功率（无发送数据显示"今日暂无发送"）
        String today = DateUtil.format(now, "yyyyMMdd");
        // 渠道健康列表始终基于全量账号（保证下拉选项完整）；counts 的活跃渠道数基于过滤后的账号
        Map<Integer, List<ChannelAccount>> activeByChannel = accounts.stream()
                .filter(item -> Objects.nonNull(item.getSendChannel()))
                .collect(Collectors.groupingBy(ChannelAccount::getSendChannel));
        Map<Integer, List<ChannelAccount>> byChannel = allAccounts.stream()
                .filter(item -> Objects.nonNull(item.getSendChannel()))
                .collect(Collectors.groupingBy(ChannelAccount::getSendChannel));
        List<ChannelHealthVo> channels = new ArrayList<>(byChannel.size());
        byChannel.forEach((code, channelAccounts) -> {
            long ok = dashboardCounterService.readChannelDay(code, "success", today);
            long bad = dashboardCounterService.readChannelDay(code, "fail", today);
            long total = ok + bad;
            Double rate = total > 0 ? Math.round(ok * 1000.0 / total) / 10.0 : null;
            String state;
            if (rate == null) {
                state = "今日暂无发送";
            } else if (rate >= 95D) {
                state = "正常";
            } else if (rate >= 80D) {
                state = "关注";
            } else {
                state = "异常";
            }
            channels.add(ChannelHealthVo.builder()
                    .channelCode(code)
                    .channelName(channelName(code))
                    .accountCount(channelAccounts.size())
                    .total(total)
                    .successRate(rate)
                    .state(state)
                    .build());
        });
        channels.sort(Comparator.comparing(ChannelHealthVo::getChannelCode));

        Double successRate = (submitted > 0) ? Math.round(succeeded * 1000.0 / submitted) / 10.0 : null;

        return DashboardOverviewVo.builder()
                .tenant(TenantQuotaVo.builder()
                        .tenantId(account.getTenantId())
                        .availableQuota(account.getAvailableQuota())
                        .reservedQuota(account.getReservedQuota())
                        .warningThreshold(account.getWarningThreshold())
                        .build())
                .counts(CountsVo.builder()
                        .templates(templateCount)
                        .channelAccounts(accounts.size())
                        .activeChannels(activeByChannel.size())
                        .build())
                .metrics(MetricsVo.builder()
                        .submitted(submitted)
                        .succeeded(succeeded)
                        .failed(failed)
                        .filtered(filtered)
                        .unknown(unknown)
                        .successRate(successRate)
                        .build())
                .trend(trend)
                .channels(channels)
                .build();
    }

    /**
     * 统一的指标读取入口：全局聚合读全局桶，指定渠道读渠道桶
     */
    private long readMetric(String metric, boolean byDay, String bucket, Integer channelCode) {
        if (channelCode == null) {
            return byDay ? dashboardCounterService.readDay(metric, bucket)
                    : dashboardCounterService.readHour(metric, bucket);
        }
        return byDay ? dashboardCounterService.readChannelDay(channelCode, metric, bucket)
                : dashboardCounterService.readChannelHour(channelCode, metric, bucket);
    }

    private String channelName(Integer code) {
        for (ChannelType type : ChannelType.values()) {
            if (type.getCode().equals(code)) {
                return type.getDescription();
            }
        }
        return "未知渠道(" + code + ")";
    }
}
