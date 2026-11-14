package com.java3y.austin.handler.receiver.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.domain.AnchorInfo;
import com.java3y.austin.common.domain.LogParam;
import com.java3y.austin.common.domain.RecallTaskInfo;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.AnchorState;
import com.java3y.austin.handler.handler.HandlerHolder;
import com.java3y.austin.handler.inbox.InboxDispatchService;
import com.java3y.austin.handler.inbox.InboxMessage;
import com.java3y.austin.handler.inbox.InboxRegistrationResult;
import com.java3y.austin.handler.inbox.InboxService;
import com.java3y.austin.handler.receiver.service.ConsumeService;
import com.java3y.austin.handler.utils.GroupIdMappingUtils;
import com.java3y.austin.support.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 */
@Service
public class ConsumeServiceImpl implements ConsumeService {
    private static final String LOG_BIZ_TYPE = "Receiver#consumer";
    private static final String LOG_BIZ_RECALL_TYPE = "Receiver#recall";
    @Autowired
    private InboxService inboxService;

    @Autowired
    private InboxDispatchService inboxDispatchService;

    @Value("${austin.mq.inbox.max-attempts:5}")
    private int maxAttempts;

    @Autowired
    private LogUtils logUtils;

    @Autowired
    private HandlerHolder handlerHolder;

    @Override
    public void consume2Send(List<TaskInfo> taskInfoLists) {
        for (TaskInfo taskInfo : taskInfoLists) {
            if (taskInfo.getTenantId() == null) {
                taskInfo.setTenantId(AustinConstant.DEFAULT_TENANT_ID);
            }
            String topicGroupId = GroupIdMappingUtils.getGroupIdByTaskInfo(taskInfo);
            String payload = JSON.toJSONString(taskInfo, JSONWriter.Feature.WriteClassName);
            String consumeKey;
            if (CharSequenceUtil.isNotBlank(taskInfo.getDeliveryAttemptId())) {
                consumeKey = "delivery:" + DigestUtil.sha256Hex(taskInfo.getMessageId()
                        + "|" + taskInfo.getDeliveryAttemptId());
            } else {
                consumeKey = CharSequenceUtil.isNotBlank(taskInfo.getMessageId())
                        ? taskInfo.getMessageId() : "legacy:" + DigestUtil.sha256Hex(payload);
            }
            InboxRegistrationResult registration = inboxService.register(taskInfo.getTenantId(),
                    topicGroupId, consumeKey, payload, maxAttempts);
            InboxMessage inboxMessage = registration.getMessage();
            logUtils.print(LogParam.builder().bizType(LOG_BIZ_TYPE).object(taskInfo).build(), AnchorInfo.builder().bizId(taskInfo.getBizId()).messageId(taskInfo.getMessageId()).ids(taskInfo.getReceiver()).businessId(taskInfo.getBusinessId()).state(AnchorState.RECEIVE.getCode()).build());
            inboxDispatchService.dispatch(inboxMessage.getTenantId(), inboxMessage.getId(),
                    inboxMessage.getConsumerGroup());
        }
    }

    @Override
    public void consume2recall(RecallTaskInfo recallTaskInfo) {
        logUtils.print(LogParam.builder().bizType(LOG_BIZ_RECALL_TYPE).object(recallTaskInfo).build());
        handlerHolder.route(recallTaskInfo.getSendChannel()).recall(recallTaskInfo);
    }
}
