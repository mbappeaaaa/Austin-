package com.java3y.austin.web.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.domain.MessageTemplate;
import com.java3y.austin.support.service.ChannelAccountAvailabilityService;
import com.java3y.austin.support.service.EnvironmentEmailAccountProvider;
import com.java3y.austin.support.service.EnvironmentFeiShuRobotAccountProvider;
import com.java3y.austin.web.exception.CommonException;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.java3y.austin.common.dto.model.WebhookContentModel;

/**
 * 模板持久化和测试发送共用的渠道账号前置校验。
 */
@Component
public class MessageTemplateChannelValidator {

    private final ChannelAccountAvailabilityService availabilityService;

    public MessageTemplateChannelValidator(ChannelAccountAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    public void validate(MessageTemplate messageTemplate) {
        ChannelAvailability availability = check(messageTemplate);
        if (!availability.isReady()) {
            throw new CommonException("渠道不可用（" + availability.getStatus() + "）：" + availability.getMessage());
        }
        validateContent(messageTemplate);
    }

    public ChannelAvailability check(MessageTemplate messageTemplate) {
        if (messageTemplate == null) {
            return ChannelAvailability.builder().status(ChannelAvailabilityStatus.NOT_CONFIGURED)
                    .message("消息模板不存在，无法检查渠道账号").build();
        }
        String creator = CharSequenceUtil.isBlank(messageTemplate.getCreator())
                ? AustinConstant.DEFAULT_CREATOR : messageTemplate.getCreator();
        Integer sendAccount = messageTemplate.getSendAccount();
        boolean environmentEmail = ChannelType.EMAIL.getCode().equals(messageTemplate.getSendChannel())
                && Integer.valueOf(EnvironmentEmailAccountProvider.ENVIRONMENT_EMAIL_ACCOUNT_ID).equals(sendAccount);
        boolean environmentFeiShuRobot = ChannelType.FEI_SHU_ROBOT.getCode().equals(messageTemplate.getSendChannel())
                && Integer.valueOf(EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID)
                .equals(sendAccount);
        if (!ChannelType.SMS.getCode().equals(messageTemplate.getSendChannel())
                && !ChannelType.IM.getCode().equals(messageTemplate.getSendChannel()) && !environmentEmail
                && !environmentFeiShuRobot
                && (sendAccount == null || sendAccount <= 0)) {
            return ChannelAvailability.builder().status(ChannelAvailabilityStatus.NOT_CONFIGURED)
                    .message("请先选择已配置的渠道账号").build();
        }
        ChannelAvailability availability = ChannelType.SMS.getCode().equals(messageTemplate.getSendChannel())
                && (sendAccount == null || sendAccount <= 0)
                ? availabilityService.checkSmsFlow(messageTemplate.getMsgType(), messageTemplate.getTenantId())
                : availabilityService.check(messageTemplate.getSendChannel(), sendAccount, creator,
                messageTemplate.getTenantId());
        return availability;
    }

    private void validateContent(MessageTemplate messageTemplate) {
        if (ChannelType.WEBHOOK.getCode().equals(messageTemplate.getSendChannel())) {
            try {
                JSONObject template = JSON.parseObject(messageTemplate.getMsgContent());
                String body = template == null ? null : template.getString("body");
                Object jsonBody = JSON.parse(body);
                if (!(jsonBody instanceof JSONObject) && !(jsonBody instanceof com.alibaba.fastjson2.JSONArray)) {
                    throw new IllegalArgumentException("not object or array");
                }
            } catch (Exception e) {
                throw new CommonException("Webhook 模板的 body 必须是有效的 JSON 对象或数组字符串");
            }
        }
    }
}
