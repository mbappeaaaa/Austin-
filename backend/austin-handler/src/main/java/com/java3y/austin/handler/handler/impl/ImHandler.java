package com.java3y.austin.handler.handler.impl;

import com.java3y.austin.common.domain.RecallTaskInfo;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.handler.handler.BaseHandler;
import com.java3y.austin.support.service.InAppMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ImHandler extends BaseHandler {

    private final InAppMessageService inAppMessageService;

    public ImHandler(InAppMessageService inAppMessageService) {
        this.inAppMessageService = inAppMessageService;
        this.channelCode = ChannelType.IM.getCode();
    }

    @Override
    public boolean handler(TaskInfo taskInfo) {
        try {
            return inAppMessageService.store(taskInfo) >= 0;
        } catch (Exception e) {
            log.error("ImHandler store failed, messageId:{}, type:{}", taskInfo.getMessageId(),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void recall(RecallTaskInfo recallTaskInfo) {
        // 站内信由用户侧逻辑删除，不支持按模板撤回。
    }
}
