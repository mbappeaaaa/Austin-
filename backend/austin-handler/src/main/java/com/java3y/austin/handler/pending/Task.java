package com.java3y.austin.handler.pending;


import com.alibaba.fastjson2.JSON;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.pipeline.ProcessController;
import com.java3y.austin.common.pipeline.ProcessModel;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.handler.config.TaskPipelineConfig;
import com.java3y.austin.handler.delivery.NonRetryableDeliveryException;
import com.java3y.austin.handler.inbox.InboxService;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Task 执行器
 *
 */
@Data
@Accessors(chain = true)
@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Task implements Runnable {
    private Long inboxId;
    private Long tenantId;
    private String workerId;
    private String payload;
    private long retryBaseSeconds;
    @Autowired
    private InboxService inboxService;
    @Autowired
    @Qualifier("handlerProcessController")
    private ProcessController processController;

    @Override
    public void run() {
        try {
            TaskInfo taskInfo = JSON.parseObject(payload, TaskInfo.class);
            ProcessContext<ProcessModel> context = ProcessContext.builder()
                    .processModel(taskInfo).code(TaskPipelineConfig.PIPELINE_HANDLER_CODE)
                    .needBreak(false).response(BasicResultVO.success())
                    .build();
            processController.process(context);
            inboxService.succeed(tenantId, inboxId, workerId);
        } catch (NonRetryableDeliveryException exception) {
            try {
                inboxService.deadLetter(tenantId, inboxId, workerId, exception.getErrorCode(),
                        exception.getMessage());
            } catch (RuntimeException stateFailure) {
                log.error("Inbox dead-letter transition failed, inboxId:{}, type:{}",
                        inboxId, stateFailure.getClass().getSimpleName());
            }
            log.error("Inbox message requires manual reconciliation, inboxId:{}, code:{}",
                    inboxId, exception.getErrorCode());
        } catch (RuntimeException exception) {
            try {
                inboxService.fail(tenantId, inboxId, workerId, exception.getClass().getSimpleName(),
                        "message processing failed", retryBaseSeconds);
            } catch (RuntimeException stateFailure) {
                log.error("Inbox failure transition failed, inboxId:{}, type:{}",
                        inboxId, stateFailure.getClass().getSimpleName());
            }
            log.error("Inbox message processing failed, inboxId:{}, type:{}",
                    inboxId, exception.getClass().getSimpleName());
        }
    }
}
