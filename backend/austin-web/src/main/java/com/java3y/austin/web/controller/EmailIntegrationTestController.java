package com.java3y.austin.web.controller;

import cn.hutool.core.text.CharSequenceUtil;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.dto.model.EmailContentModel;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.handler.handler.impl.EmailHandler;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.service.EnvironmentEmailAccountProvider;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.exception.CommonException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SMTP 集成测试入口。默认不注册，并且收件人只能来自 AUSTIN_EMAIL_TEST_TO。
 */
@AustinAspect
@AustinResult
@RestController
@RequestMapping("/email/integration-test")
@ConditionalOnProperty(name = "austin.email.test.enabled", havingValue = "true")
@Api("邮箱渠道集成测试")
public class EmailIntegrationTestController {

    private final EmailHandler emailHandler;
    private final EnvironmentEmailAccountProvider environmentEmailAccountProvider;

    public EmailIntegrationTestController(EmailHandler emailHandler,
                                          EnvironmentEmailAccountProvider environmentEmailAccountProvider) {
        this.emailHandler = emailHandler;
        this.environmentEmailAccountProvider = environmentEmailAccountProvider;
    }

    @PostMapping("/send")
    @ApiOperation("只向环境变量指定的邮箱发送测试邮件")
    public Map<String, String> send() {
        ChannelAvailability availability = environmentEmailAccountProvider.availability();
        if (!availability.isReady()) {
            throw new CommonException("邮箱渠道不可用：" + availability.getMessage());
        }
        String recipient = environmentEmailAccountProvider.getTestRecipient();
        if (CharSequenceUtil.isBlank(recipient)) {
            throw new CommonException("未配置 AUSTIN_EMAIL_TEST_TO，禁止发送集成测试邮件");
        }

        EmailContentModel content = EmailContentModel.builder()
                .title("Austin 邮箱渠道集成测试")
                .content("<p>Austin EmailHandler SMTP 集成测试成功。</p><p>发送时间："
                        + LocalDateTime.now() + "</p>")
                .build();
        TaskInfo taskInfo = TaskInfo.builder()
                .receiver(Collections.singleton(recipient))
                .sendChannel(ChannelType.EMAIL.getCode())
                .sendAccount(EnvironmentEmailAccountProvider.ENVIRONMENT_EMAIL_ACCOUNT_ID)
                .contentModel(content)
                .build();
        if (!emailHandler.handler(taskInfo)) {
            throw new CommonException("测试邮件发送失败，请检查 Austin 日志中的 EmailHandler 错误");
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", "测试邮件已提交给 SMTP 服务器");
        result.put("recipient", maskEmail(recipient));
        return result;
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
