package com.java3y.austin.web.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.service.EnvironmentFeiShuRobotAccountProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 飞书机器人测试入口的独立管理员令牌校验。
 * Austin 当前没有通用 RBAC，因此使用仅存在于运行环境中的高强度令牌保护开发测试入口。
 */
@Component
public class FeiShuRobotTestAccessGuard implements InitializingBean {

    private static final String TEST_ENABLED = "austin.feishu.robot.test.enabled";
    private static final String ADMIN_TOKEN = "austin.feishu.robot.test.admin-token";
    private static final int MIN_TOKEN_LENGTH = 24;

    private final Environment environment;
    private final EnvironmentFeiShuRobotAccountProvider accountProvider;

    public FeiShuRobotTestAccessGuard(Environment environment,
                                      EnvironmentFeiShuRobotAccountProvider accountProvider) {
        this.environment = environment;
        this.accountProvider = accountProvider;
    }

    @Override
    public void afterPropertiesSet() {
        if (!isTestEnabled()) {
            return;
        }
        if (!environment.acceptsProfiles(Profiles.of("dev", "local"))) {
            throw new IllegalStateException("飞书机器人测试接口只能在 dev 或 local 环境启用");
        }
        ChannelAvailability availability = accountProvider.availability();
        if (!availability.isReady()) {
            throw new IllegalStateException("启用飞书机器人测试接口时渠道必须可用：" + availability.getMessage());
        }
        String token = configuredToken();
        if (CharSequenceUtil.isBlank(token) || token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException("启用飞书机器人测试接口时，AUSTIN_FEISHU_TEST_ADMIN_TOKEN 至少需要 24 个字符");
        }
    }

    public boolean isAdministrator(String suppliedToken) {
        String expectedToken = configuredToken();
        if (!isTestEnabled() || CharSequenceUtil.isBlank(expectedToken) || CharSequenceUtil.isBlank(suppliedToken)) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isTestEnabled() {
        return environment.getProperty(TEST_ENABLED, Boolean.class, false);
    }

    private String configuredToken() {
        return environment.getProperty(ADMIN_TOKEN);
    }
}
