package com.java3y.austin.support.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.java3y.austin.common.dto.account.FeiShuRobotAccount;
import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.support.domain.ChannelAvailability;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * 从环境变量或外部安全配置中读取飞书群机器人 webhook。
 * webhook 只保留在进程内存中，不写入数据库，也不输出到日志。
 */
@Component
public class EnvironmentFeiShuRobotAccountProvider implements InitializingBean {

    public static final int ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID = -110;
    public static final String ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_NAME = "环境变量飞书机器人";

    private static final String ENABLED = "austin.feishu.robot.enabled";
    private static final String WEBHOOK = "austin.feishu.robot.webhook";

    private final Environment environment;

    public EnvironmentFeiShuRobotAccountProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (isEnabled()) {
            ChannelAvailability availability = availability();
            if (!availability.isReady()) {
                throw new IllegalStateException("飞书机器人启动配置校验失败：" + availability.getMessage());
            }
        }
    }

    public ChannelAvailability availability() {
        if (!isEnabled()) {
            return unavailable("飞书机器人未启用，请设置 AUSTIN_FEISHU_ROBOT_ENABLED=true");
        }
        String webhook = environment.getProperty(WEBHOOK);
        if (CharSequenceUtil.isBlank(webhook)) {
            return unavailable("缺少 AUSTIN_FEISHU_ROBOT_WEBHOOK");
        }
        if (!isValidWebhook(webhook)) {
            return unavailable("AUSTIN_FEISHU_ROBOT_WEBHOOK 必须是飞书官方 HTTPS 群机器人地址");
        }
        return ChannelAvailability.builder().status(ChannelAvailabilityStatus.READY)
                .message("环境变量飞书机器人已配置，可以发送").build();
    }

    public FeiShuRobotAccount getAccount() {
        ChannelAvailability availability = availability();
        if (!availability.isReady()) {
            throw new IllegalStateException(availability.getMessage());
        }
        return FeiShuRobotAccount.builder().webhook(environment.getProperty(WEBHOOK).trim()).build();
    }

    private boolean isEnabled() {
        return environment.getProperty(ENABLED, Boolean.class, false);
    }

    private boolean isValidWebhook(String webhook) {
        try {
            URI uri = URI.create(webhook.trim());
            String host = uri.getHost();
            String path = uri.getPath();
            boolean officialHost = "open.feishu.cn".equalsIgnoreCase(host)
                    || "open.larksuite.com".equalsIgnoreCase(host);
            return "https".equalsIgnoreCase(uri.getScheme()) && officialHost
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && CharSequenceUtil.isBlank(uri.getUserInfo())
                    && path != null && path.startsWith("/open-apis/bot/v2/hook/")
                    && path.length() > "/open-apis/bot/v2/hook/".length()
                    && CharSequenceUtil.isBlank(uri.getQuery()) && CharSequenceUtil.isBlank(uri.getFragment());
        } catch (Exception ignored) {
            return false;
        }
    }

    private ChannelAvailability unavailable(String message) {
        return ChannelAvailability.builder().status(ChannelAvailabilityStatus.NOT_CONFIGURED)
                .message(message).build();
    }
}
