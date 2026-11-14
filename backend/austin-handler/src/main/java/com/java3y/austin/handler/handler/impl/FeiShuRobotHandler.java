package com.java3y.austin.handler.handler.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSON;
import com.java3y.austin.common.domain.RecallTaskInfo;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.dto.account.FeiShuRobotAccount;
import com.java3y.austin.common.dto.model.FeiShuRobotContentModel;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.common.enums.SendMessageType;
import com.java3y.austin.handler.domain.feishu.FeiShuRobotParam;
import com.java3y.austin.handler.domain.feishu.FeiShuRobotResult;
import com.java3y.austin.handler.domain.feishu.FeiShuRobotSendResult;
import com.java3y.austin.handler.handler.BaseHandler;
import com.java3y.austin.support.service.EnvironmentFeiShuRobotAccountProvider;
import com.java3y.austin.support.utils.AccountUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 飞书自定义机器人 消息处理器
 * https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot
 */
@Slf4j
@Service
public class FeiShuRobotHandler extends BaseHandler{

    private static final int LOCAL_ERROR_CODE = -1;
    private static final int MAX_SAFE_REASON_LENGTH = 200;

    @Autowired
    private AccountUtils accountUtils;

    @Autowired
    private EnvironmentFeiShuRobotAccountProvider environmentFeiShuRobotAccountProvider;

    public FeiShuRobotHandler() {
        channelCode = ChannelType.FEI_SHU_ROBOT.getCode();
    }

    @Override
    public boolean handler(TaskInfo taskInfo) {
        FeiShuRobotSendResult result = sendWithResult(taskInfo);
        if (!result.isSuccess()) {
            log.error("FeiShuRobotHandler#handler fail! responseCode:{},reason:{},params:{}",
                    result.getResponseCode(), result.getErrorReason(), JSON.toJSONString(taskInfo));
        }
        return result.isSuccess();
    }

    /**
     * 复用正式 Handler 的参数组装及 HTTP 发送，并向受限测试入口提供飞书响应码。
     */
    public FeiShuRobotSendResult sendWithResult(TaskInfo taskInfo) {
        try {
            FeiShuRobotAccount account = getAccount(taskInfo.getSendAccount());
            FeiShuRobotParam feiShuRobotParam = assembleParam(taskInfo);
            String result = HttpRequest.post(account.getWebhook())
                    .header(Header.CONTENT_TYPE.getValue(), ContentType.JSON.getValue())
                    .body(JSON.toJSONString(feiShuRobotParam))
                    .timeout(2000)
                    .execute().body();
            FeiShuRobotResult feiShuRobotResult = JSON.parseObject(result, FeiShuRobotResult.class);
            Integer responseCode = effectiveResponseCode(feiShuRobotResult);
            boolean success = Integer.valueOf(0).equals(responseCode);
            return FeiShuRobotSendResult.builder().success(success).responseCode(responseCode)
                    .errorReason(success ? "" : maskErrorReason(effectiveErrorReason(feiShuRobotResult))).build();
        } catch (Exception e) {
            log.error("FeiShuRobotHandler#send fail! type:{},params:{}", e.getClass().getSimpleName(), JSON.toJSONString(taskInfo));
            return FeiShuRobotSendResult.builder().success(false).responseCode(LOCAL_ERROR_CODE)
                    .errorReason(maskErrorReason(e.getMessage())).build();
        }
    }

    private FeiShuRobotAccount getAccount(Integer accountId) {
        if (Integer.valueOf(EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID).equals(accountId)) {
            return environmentFeiShuRobotAccountProvider.getAccount();
        }
        return accountUtils.getAccountById(accountId, FeiShuRobotAccount.class);
    }

    private Integer effectiveResponseCode(FeiShuRobotResult result) {
        if (result == null) {
            return LOCAL_ERROR_CODE;
        }
        return result.getStatusCode() != null ? result.getStatusCode()
                : (result.getCode() != null ? result.getCode() : LOCAL_ERROR_CODE);
    }

    private String effectiveErrorReason(FeiShuRobotResult result) {
        if (result == null) {
            return "飞书返回了无法解析的响应";
        }
        return CharSequenceUtil.isNotBlank(result.getStatusMessage())
                ? result.getStatusMessage() : result.getMsg();
    }

    private String maskErrorReason(String reason) {
        if (CharSequenceUtil.isBlank(reason)) {
            return "飞书未返回错误原因";
        }
        String masked = reason.replaceAll("(?i)https?://\\S+", "[已脱敏URL]")
                .replaceAll("(?i)(token|secret|webhook)(\\s*[=:]\\s*)[^\\s,;]+", "$1$2***")
                .replaceAll("[A-Za-z0-9_-]{24,}", "***");
        return masked.length() <= MAX_SAFE_REASON_LENGTH
                ? masked : masked.substring(0, MAX_SAFE_REASON_LENGTH) + "...";
    }

    /**
     * 飞书自定义机器人 暂只支持文本消息下发，其他的消息类型的组装参数过于复杂。
     *
     * @param taskInfo
     * @return
     */
    private FeiShuRobotParam assembleParam(TaskInfo taskInfo) {
        FeiShuRobotContentModel contentModel = (FeiShuRobotContentModel) taskInfo.getContentModel();

        FeiShuRobotParam param = FeiShuRobotParam.builder()
                .msgType(SendMessageType.geFeiShuRobotTypeByCode(contentModel.getSendType())).build();

        if (SendMessageType.TEXT.getCode().equals(contentModel.getSendType())) {
            param.setContent(FeiShuRobotParam.ContentDTO.builder().text(contentModel.getContent()).build());
        }
        if (SendMessageType.RICH_TEXT.getCode().equals(contentModel.getSendType())) {
            List<FeiShuRobotParam.ContentDTO.PostDTO.ZhCnDTO.PostContentDTO> postContentDtoS = JSON.parseArray(contentModel.getPostContent(), FeiShuRobotParam.ContentDTO.PostDTO.ZhCnDTO.PostContentDTO.class);
            List<List<FeiShuRobotParam.ContentDTO.PostDTO.ZhCnDTO.PostContentDTO>> postContentList = new ArrayList<>();
            postContentList.add(postContentDtoS);
            FeiShuRobotParam.ContentDTO.PostDTO postDTO = FeiShuRobotParam.ContentDTO.PostDTO.builder()
                    .zhCn(FeiShuRobotParam.ContentDTO.PostDTO.ZhCnDTO.builder().title(contentModel.getTitle()).content(postContentList).build())
                    .build();
            param.setContent(FeiShuRobotParam.ContentDTO.builder().post(postDTO).build());
        }
        if (SendMessageType.SHARE_CHAT.getCode().equals(contentModel.getSendType())) {
            param.setContent(FeiShuRobotParam.ContentDTO.builder().shareChatId(contentModel.getMediaId()).build());
        }
        if (SendMessageType.IMAGE.getCode().equals(contentModel.getSendType())) {
            param.setContent(FeiShuRobotParam.ContentDTO.builder().imageKey(contentModel.getMediaId()).build());
        }
        if (SendMessageType.ACTION_CARD.getCode().equals(contentModel.getSendType())) {
            //
        }
        return param;
    }


    /**
     * 飞书自定义机器人 不支持撤回消息
     * https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot
     * @param recallTaskInfo
     */
    @Override
    public void recall(RecallTaskInfo recallTaskInfo) {
    }
}
