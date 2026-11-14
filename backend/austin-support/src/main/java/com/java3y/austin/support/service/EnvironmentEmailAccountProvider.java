package com.java3y.austin.support.service;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.extra.mail.MailAccount;
import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.support.domain.ChannelAvailability;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从环境变量映射的 Spring 配置中创建 SMTP 账号。
 * 授权码只保留在进程内存中，不写入数据库，也不输出到日志。
 */
@Component
public class EnvironmentEmailAccountProvider {

    public static final int ENVIRONMENT_EMAIL_ACCOUNT_ID = -40;
    public static final String ENVIRONMENT_EMAIL_ACCOUNT_NAME = "环境变量 SMTP";

    private static final String HOST = "austin.email.smtp.host";
    private static final String PORT = "austin.email.smtp.port";
    private static final String USER = "austin.email.smtp.user";
    private static final String FROM = "austin.email.smtp.from";
    private static final String AUTH_CODE = "austin.email.smtp.auth-code";
    private static final String SSL_ENABLED = "austin.email.smtp.ssl-enabled";
    private static final String STARTTLS_ENABLED = "austin.email.smtp.starttls-enabled";
    private static final String TEST_TO = "austin.email.test.to";

    private final Environment environment;

    public EnvironmentEmailAccountProvider(Environment environment) {
        this.environment = environment;
    }

    public ChannelAvailability availability() {
        List<String> missing = new ArrayList<>();
        required(missing, HOST, "AUSTIN_SMTP_HOST");
        required(missing, PORT, "AUSTIN_SMTP_PORT");
        required(missing, USER, "AUSTIN_SMTP_USER");
        required(missing, FROM, "AUSTIN_SMTP_FROM");
        required(missing, AUTH_CODE, "AUSTIN_SMTP_AUTH_CODE");
        if (!missing.isEmpty()) {
            return ChannelAvailability.builder().status(ChannelAvailabilityStatus.NOT_CONFIGURED)
                    .message("环境变量 SMTP 尚未配置完整，缺少：" + String.join("、", missing)).build();
        }
        try {
            int port = Integer.parseInt(environment.getProperty(PORT));
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("out of range");
            }
        } catch (NumberFormatException e) {
            return ChannelAvailability.builder().status(ChannelAvailabilityStatus.NOT_CONFIGURED)
                    .message("AUSTIN_SMTP_PORT 必须是 1-65535 的端口号").build();
        }
        return ChannelAvailability.builder().status(ChannelAvailabilityStatus.READY)
                .message("环境变量 SMTP 已配置，可以发送").build();
    }

    public MailAccount getAccount() {
        ChannelAvailability availability = availability();
        if (!availability.isReady()) {
            throw new IllegalStateException(availability.getMessage());
        }
        return new MailAccount()
                .setHost(environment.getProperty(HOST))
                .setPort(Integer.valueOf(environment.getProperty(PORT)))
                .setUser(environment.getProperty(USER))
                .setPass(environment.getProperty(AUTH_CODE))
                .setFrom(environment.getProperty(FROM))
                .setAuth(true)
                .setSslEnable(environment.getProperty(SSL_ENABLED, Boolean.class, true))
                .setStarttlsEnable(environment.getProperty(STARTTLS_ENABLED, Boolean.class, false));
    }

    public String getTestRecipient() {
        return environment.getProperty(TEST_TO);
    }

    private void required(List<String> missing, String property, String environmentVariable) {
        if (CharSequenceUtil.isBlank(environment.getProperty(property))) {
            missing.add(environmentVariable);
        }
    }
}
