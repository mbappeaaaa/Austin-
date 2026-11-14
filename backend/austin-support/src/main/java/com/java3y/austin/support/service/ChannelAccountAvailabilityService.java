package com.java3y.austin.support.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.java3y.austin.common.constant.CommonConstant;
import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.support.dao.ChannelAccountDao;
import com.java3y.austin.support.domain.ChannelAccount;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.utils.WebhookSecurityUtils;
import com.java3y.austin.common.dto.account.WebhookAccount;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 渠道账号可用性检查。保存模板、测试发送和账号下拉框共用同一套规则。
 */
@Service
public class ChannelAccountAvailabilityService {

    public static final String MOCK_SMS_SCRIPT = "MockSmsScript";
    public static final String TENCENT_SMS_SCRIPT = "TencentSmsScript";
    public static final String YUN_PIAN_SMS_SCRIPT = "YunPianSmsScript";
    private static final String LIN_TONG_SMS_SCRIPT = "LinTongSmsScript";

    private final ChannelAccountDao channelAccountDao;
    private final Environment environment;
    private final ConfigService configService;
    private final EnvironmentEmailAccountProvider environmentEmailAccountProvider;
    private final EnvironmentFeiShuRobotAccountProvider environmentFeiShuRobotAccountProvider;
    private final ChannelPolicyService channelPolicyService;

    public ChannelAccountAvailabilityService(ChannelAccountDao channelAccountDao, Environment environment,
                                               ConfigService configService,
                                               EnvironmentEmailAccountProvider environmentEmailAccountProvider,
                                               EnvironmentFeiShuRobotAccountProvider environmentFeiShuRobotAccountProvider) {
        this.channelAccountDao = channelAccountDao;
        this.environment = environment;
        this.configService = configService;
        this.environmentEmailAccountProvider = environmentEmailAccountProvider;
        this.environmentFeiShuRobotAccountProvider = environmentFeiShuRobotAccountProvider;
        this.channelPolicyService = new ChannelPolicyService(environment);
    }

    public ChannelAvailability check(Integer channelType, Integer accountId, String creator) {
        return check(channelType, accountId, creator, null);
    }

    public ChannelAvailability check(Integer channelType, Integer accountId, String creator, Long tenantId) {
        if (!channelPolicyService.isEnabled(channelType)) {
            return disabled(channelType);
        }
        if (ChannelType.IM.getCode().equals(channelType)) {
            return ready(false, "IM 站内信已启用，无需配置外部账号");
        }
        if (ChannelType.EMAIL.getCode().equals(channelType)
                && Objects.equals(EnvironmentEmailAccountProvider.ENVIRONMENT_EMAIL_ACCOUNT_ID, accountId)) {
            return environmentEmailAccountProvider == null
                    ? unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "环境变量 SMTP 未启用")
                    : environmentEmailAccountProvider.availability();
        }
        if (ChannelType.FEI_SHU_ROBOT.getCode().equals(channelType)
                && Objects.equals(EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID, accountId)) {
            return environmentFeiShuRobotAccountProvider == null
                    ? unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "环境变量飞书机器人未启用")
                    : environmentFeiShuRobotAccountProvider.availability();
        }
        if (Objects.isNull(accountId) || accountId <= 0) {
            return checkChannel(channelType, creator);
        }
        Optional<ChannelAccount> account = tenantId == null
                ? channelAccountDao.findById(Long.valueOf(accountId))
                : channelAccountDao.findByIdAndTenantId(Long.valueOf(accountId), tenantId);
        if (!account.isPresent() || !CommonConstant.FALSE.equals(account.get().getIsDeleted())
                || !Objects.equals(channelType, account.get().getSendChannel())
                || (CharSequenceUtil.isNotBlank(creator) && !Objects.equals(creator, account.get().getCreator()))) {
            return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "未找到当前用户可用的渠道账号");
        }
        return evaluate(account.get());
    }

    public ChannelAvailability checkChannel(Integer channelType, String creator) {
        return checkChannel(channelType, creator, null);
    }

    public ChannelAvailability checkChannel(Integer channelType, String creator, Long tenantId) {
        if (!channelPolicyService.isEnabled(channelType)) {
            return disabled(channelType);
        }
        if (ChannelType.IM.getCode().equals(channelType)) {
            return ready(false, "IM 站内信已启用，无需配置外部账号");
        }
        if (ChannelType.EMAIL.getCode().equals(channelType) && environmentEmailAccountProvider != null) {
            ChannelAvailability environmentAvailability = environmentEmailAccountProvider.availability();
            if (environmentAvailability.isReady()) {
                return environmentAvailability;
            }
        }
        if (ChannelType.FEI_SHU_ROBOT.getCode().equals(channelType)
                && environmentFeiShuRobotAccountProvider != null) {
            ChannelAvailability environmentAvailability = environmentFeiShuRobotAccountProvider.availability();
            if (environmentAvailability.isReady()) {
                return environmentAvailability;
            }
        }
        List<ChannelAccount> accounts;
        if (tenantId == null) {
            accounts = CharSequenceUtil.isBlank(creator)
                    ? channelAccountDao.findAllByIsDeletedEqualsAndSendChannelEquals(CommonConstant.FALSE, channelType)
                    : channelAccountDao.findAllByIsDeletedEqualsAndCreatorEqualsAndSendChannelEquals(
                    CommonConstant.FALSE, creator, channelType);
        } else {
            accounts = CharSequenceUtil.isBlank(creator)
                    ? channelAccountDao.findAllByTenantIdAndIsDeletedEqualsAndSendChannelEquals(
                    tenantId, CommonConstant.FALSE, channelType)
                    : channelAccountDao.findAllByTenantIdAndIsDeletedEqualsAndCreatorEqualsAndSendChannelEquals(
                    tenantId, CommonConstant.FALSE, creator, channelType);
        }
        ChannelAvailability fallback = defaultUnavailable(channelType);
        for (ChannelAccount account : accounts) {
            ChannelAvailability availability = evaluate(account);
            if (availability.isReady()) {
                return availability;
            }
            fallback = availability;
        }
        return fallback;
    }

    /**
     * 短信流量配置按脚本路由时，确认该脚本确实存在可用账号。
     */
    public ChannelAvailability checkSmsScript(String scriptName, Integer accountId) {
        return checkSmsScript(scriptName, accountId, null);
    }

    public ChannelAvailability checkSmsScript(String scriptName, Integer accountId, Long tenantId) {
        if (!channelPolicyService.isEnabled(ChannelType.SMS.getCode())) {
            return disabled(ChannelType.SMS.getCode());
        }
        if (!channelPolicyService.isSmsScriptEnabled(scriptName)) {
            return unavailable(ChannelAvailabilityStatus.DISABLED,
                    "短信实现 " + safeScriptName(scriptName) + " 未在启用列表中");
        }
        if (accountId != null && accountId > 0) {
            Optional<ChannelAccount> account = tenantId == null
                    ? channelAccountDao.findById(Long.valueOf(accountId))
                    : channelAccountDao.findByIdAndTenantId(Long.valueOf(accountId), tenantId);
            if (!account.isPresent() || !CommonConstant.FALSE.equals(account.get().getIsDeleted())
                    || !ChannelType.SMS.getCode().equals(account.get().getSendChannel())) {
                return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "未找到可用的短信渠道账号");
            }
            try {
                JSONObject config = JSON.parseObject(account.get().getAccountConfig());
                if (config == null || !Objects.equals(scriptName, config.getString("scriptName"))) {
                    return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "短信脚本与发送账号不匹配");
                }
            } catch (Exception e) {
                return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "短信账号配置格式错误");
            }
            return evaluate(account.get());
        }
        List<ChannelAccount> accounts = tenantId == null
                ? channelAccountDao.findAllByIsDeletedEqualsAndSendChannelEquals(
                CommonConstant.FALSE, ChannelType.SMS.getCode())
                : channelAccountDao.findAllByTenantIdAndIsDeletedEqualsAndSendChannelEquals(
                tenantId, CommonConstant.FALSE, ChannelType.SMS.getCode());
        for (ChannelAccount account : accounts) {
            try {
                JSONObject config = JSON.parseObject(account.getAccountConfig());
                if (config != null && Objects.equals(scriptName, config.getString("scriptName"))) {
                    return evaluate(account);
                }
            } catch (Exception ignored) {
                // 无效配置由最终的可用性结果统一说明。
            }
        }
        if (TENCENT_SMS_SCRIPT.equals(scriptName) || YUN_PIAN_SMS_SCRIPT.equals(scriptName)) {
            return qualificationRequired("短信流量配置选择了 " + scriptName + "，但没有配置具备资质的渠道账号");
        }
        if (MOCK_SMS_SCRIPT.equals(scriptName) && isProduction()) {
            return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "生产环境禁止使用 Mock 短信渠道");
        }
        return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "短信流量配置选择了 " + scriptName + "，但没有对应的可用账号");
    }

    /**
     * 检查 AUTO 流量配置是否至少存在一条真正可发送的短信脚本。
     */
    public ChannelAvailability checkSmsFlow(Integer messageType) {
        return checkSmsFlow(messageType, null);
    }

    public ChannelAvailability checkSmsFlow(Integer messageType, Long tenantId) {
        if (!channelPolicyService.isEnabled(ChannelType.SMS.getCode())) {
            return disabled(ChannelType.SMS.getCode());
        }
        if (configService == null) {
            return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "无法读取短信流量配置，请明确选择短信账号");
        }
        String flowConfig = configService.getProperty("msgTypeSmsConfig", CommonConstant.EMPTY_VALUE_JSON_ARRAY);
        ChannelAvailability fallback = qualificationRequired("短信 AUTO 流量配置没有对应的可用渠道账号，请明确选择已配置账号");
        try {
            JSONArray groups = JSON.parseArray(flowConfig);
            for (int i = 0; i < groups.size(); i++) {
                JSONObject group = groups.getJSONObject(i);
                for (String key : group.keySet()) {
                    if (messageType != null && !Objects.equals("message_type_" + messageType, key)) {
                        continue;
                    }
                    JSONArray scripts = group.getJSONArray(key);
                    for (int j = 0; j < scripts.size(); j++) {
                        JSONObject script = scripts.getJSONObject(j);
                        ChannelAvailability availability = checkSmsScript(
                                script.getString("scriptName"), script.getInteger("sendAccount"), tenantId);
                        if (availability.isReady()) {
                            return availability;
                        }
                        fallback = availability;
                    }
                }
            }
        } catch (Exception e) {
            return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "短信 AUTO 流量配置格式错误，请明确选择短信账号");
        }
        return fallback;
    }

    public ChannelAvailability evaluate(ChannelAccount account) {
        if (account != null && !channelPolicyService.isEnabled(account.getSendChannel())) {
            return disabled(account.getSendChannel());
        }
        if (account == null || CharSequenceUtil.isBlank(account.getAccountConfig())) {
            return defaultUnavailable(account == null ? null : account.getSendChannel());
        }
        final JSONObject config;
        try {
            config = JSON.parseObject(account.getAccountConfig());
        } catch (Exception e) {
            return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "渠道账号配置不是有效的 JSON");
        }
        if (config == null || config.isEmpty()) {
            return defaultUnavailable(account.getSendChannel());
        }

        if (ChannelType.SMS.getCode().equals(account.getSendChannel())) {
            return evaluateSms(config);
        }
        List<String> required = requiredFields(account.getSendChannel());
        if (!hasValues(config, required)) {
            return defaultUnavailable(account.getSendChannel());
        }
        if (ChannelType.WEBHOOK.getCode().equals(account.getSendChannel())) {
            try {
                WebhookAccount webhookAccount = config.to(WebhookAccount.class);
                WebhookSecurityUtils.validateUrl(webhookAccount.getWebhook());
                WebhookSecurityUtils.validateHeaders(webhookAccount.getHeaders());
            } catch (Exception e) {
                return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED,
                        "Webhook 配置不安全或目标不是可访问的公网地址");
            }
        }
        return ready(false, "渠道账号已配置，可以发送");
    }

    private ChannelAvailability evaluateSms(JSONObject config) {
        String scriptName = config.getString("scriptName");
        if (!channelPolicyService.isSmsScriptEnabled(scriptName)) {
            return unavailable(ChannelAvailabilityStatus.DISABLED,
                    "短信实现 " + safeScriptName(scriptName) + " 未在启用列表中");
        }
        if (MOCK_SMS_SCRIPT.equals(scriptName)) {
            if (isProduction()) {
                return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "生产环境禁止使用 Mock 短信渠道");
            }
            return ready(false, "Mock 短信已配置，仅记录发送结果，不会真实发送");
        }
        if (TENCENT_SMS_SCRIPT.equals(scriptName)) {
            if (!hasValues(config, Arrays.asList("url", "region", "secretId", "secretKey", "smsSdkAppId", "templateId", "signName"))) {
                return qualificationRequired("腾讯云短信需要企业资质，并且尚未配置完整账号");
            }
            return ready(true, "腾讯云短信账号已配置；该渠道需要企业资质");
        }
        if (YUN_PIAN_SMS_SCRIPT.equals(scriptName)) {
            boolean templateConfigured = hasText(config, "tplId") || hasText(config, "tpl_id");
            if (!hasValues(config, Arrays.asList("url", "apikey")) || !templateConfigured) {
                return qualificationRequired("云片短信需要企业资质，并且尚未配置完整账号");
            }
            return ready(true, "云片短信账号已配置；该渠道需要企业资质");
        }
        if (LIN_TONG_SMS_SCRIPT.equals(scriptName) && hasValues(config, Arrays.asList("url", "userName", "password"))) {
            return ready(true, "短信账号已配置；发送前请确认已具备服务商要求的资质");
        }
        return qualificationRequired("短信渠道需要企业资质，请先配置有效的短信账号；个人开发可使用 MockSmsScript");
    }

    private List<String> requiredFields(Integer channelType) {
        if (ChannelType.PUSH.getCode().equals(channelType)) {
            return Arrays.asList("appId", "appKey", "masterSecret");
        }
        if (ChannelType.EMAIL.getCode().equals(channelType)) {
            return Arrays.asList("host", "user", "pass", "from");
        }
        if (ChannelType.OFFICIAL_ACCOUNT.getCode().equals(channelType)) {
            return Arrays.asList("appId", "secret");
        }
        if (ChannelType.MINI_PROGRAM.getCode().equals(channelType)) {
            return Arrays.asList("appId", "appSecret");
        }
        if (ChannelType.ENTERPRISE_WE_CHAT.getCode().equals(channelType)) {
            return Arrays.asList("corpId", "corpSecret", "agentId");
        }
        if (ChannelType.DING_DING_ROBOT.getCode().equals(channelType)
                || ChannelType.ENTERPRISE_WE_CHAT_ROBOT.getCode().equals(channelType)
                || ChannelType.FEI_SHU_ROBOT.getCode().equals(channelType)) {
            return Collections.singletonList("webhook");
        }
        if (ChannelType.DING_DING_WORK_NOTICE.getCode().equals(channelType)) {
            return Arrays.asList("appKey", "appSecret", "agentId");
        }
        if (ChannelType.ALIPAY_MINI_PROGRAM.getCode().equals(channelType)) {
            return Arrays.asList("appId", "privateKey", "alipayPublicKey");
        }
        if (ChannelType.WEBHOOK.getCode().equals(channelType)) {
            return Collections.singletonList("webhook");
        }
        return Collections.singletonList("__any_configured_field__");
    }

    private ChannelAvailability defaultUnavailable(Integer channelType) {
        if (ChannelType.SMS.getCode().equals(channelType)) {
            return qualificationRequired("短信服务需要企业资质；个人开发请配置 Mock 短信账号");
        }
        if (ChannelType.PUSH.getCode().equals(channelType)
                || ChannelType.OFFICIAL_ACCOUNT.getCode().equals(channelType)
                || ChannelType.MINI_PROGRAM.getCode().equals(channelType)
                || ChannelType.ALIPAY_MINI_PROGRAM.getCode().equals(channelType)) {
            return unavailable(ChannelAvailabilityStatus.CLIENT_REQUIRED, "该渠道需要手机 App、小程序或其他客户端，并且尚未配置账号");
        }
        return unavailable(ChannelAvailabilityStatus.NOT_CONFIGURED, "尚未配置可用的渠道账号");
    }

    private boolean hasValues(JSONObject config, List<String> fields) {
        if (fields.size() == 1 && "__any_configured_field__".equals(fields.get(0))) {
            return !config.isEmpty();
        }
        for (String field : fields) {
            if (!hasText(config, field)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(JSONObject config, String field) {
        Object value = config.get(field);
        return value != null && CharSequenceUtil.isNotBlank(String.valueOf(value));
    }

    private boolean isProduction() {
        return environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    private ChannelAvailability ready(boolean qualificationRequired, String message) {
        return ChannelAvailability.builder().status(ChannelAvailabilityStatus.READY).message(message)
                .qualificationRequired(qualificationRequired).build();
    }

    private ChannelAvailability qualificationRequired(String message) {
        return ChannelAvailability.builder().status(ChannelAvailabilityStatus.QUALIFICATION_REQUIRED)
                .message(message).qualificationRequired(true).build();
    }

    private ChannelAvailability unavailable(ChannelAvailabilityStatus status, String message) {
        return ChannelAvailability.builder().status(status).message(message).build();
    }

    private ChannelAvailability disabled(Integer channelType) {
        return unavailable(ChannelAvailabilityStatus.DISABLED,
                "渠道 " + (channelType == null ? "unknown" : channelType) + " 未在 austin.channels.enabled 中启用");
    }

    private String safeScriptName(String scriptName) {
        return CharSequenceUtil.isBlank(scriptName) ? "unknown" : scriptName.replaceAll("[^A-Za-z0-9_-]", "");
    }
}
