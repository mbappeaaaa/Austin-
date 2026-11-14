package com.java3y.austin.cron.handler;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.google.common.base.Throwables;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.support.config.SupportThreadPoolConfig;
import com.java3y.austin.support.mq.SendMqService;
import com.java3y.austin.support.utils.RedisUtils;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;


/**
 * 夜间屏蔽的延迟处理类
 * <p>
 * example:当消息下发至austin平台时，已经是凌晨1点，业务希望此类消息在次日的早上9点推送
 *
 */
@Service
@Slf4j
public class NightShieldLazyPendingHandler {

    private static final String NIGHT_SHIELD_BUT_NEXT_DAY_SEND_KEY = "night_shield_send";

    /**
     * 业务消息按 austin.mq.pipeline 配置投递（rabbitMq/kafka/...），与主发送链路保持一致
     */
    @Autowired
    private SendMqService sendMqService;
    @Value("${austin.business.topic.name}")
    private String topicName;
    @Autowired
    private RedisUtils redisUtils;

    /**
     * 处理 夜间屏蔽(次日早上9点发送的任务)
     */
    @XxlJob("nightShieldLazyJob")
    public void execute() {
        log.info("NightShieldLazyPendingHandler#execute!");
        SupportThreadPoolConfig.getPendingSingleThreadPool().execute(() -> {
            while (redisUtils.lLen(NIGHT_SHIELD_BUT_NEXT_DAY_SEND_KEY) > 0) {
                String taskInfo = redisUtils.lPop(NIGHT_SHIELD_BUT_NEXT_DAY_SEND_KEY);
                if (CharSequenceUtil.isNotBlank(taskInfo)) {
                    try {
                        sendMqService.send(topicName, JSON.toJSONString(Collections.singletonList(JSON.parseObject(taskInfo, TaskInfo.class))
                                , JSONWriter.Feature.WriteClassName));
                    } catch (Exception e) {
                        log.error("nightShieldLazyJob send mq fail! e:{},params:{}", Throwables.getStackTraceAsString(e), taskInfo);
                    }
                }
            }
        });
    }
}
