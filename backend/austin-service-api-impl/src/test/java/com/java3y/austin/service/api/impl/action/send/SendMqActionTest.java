package com.java3y.austin.service.api.impl.action.send;

import com.java3y.austin.billing.service.QuotaService;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.service.api.impl.domain.SendTaskModel;
import com.java3y.austin.support.mq.SendMqService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SendMqActionTest {

    @Test
    void shouldKeepReservationUnknownWhenMqOutcomeIsAmbiguous() {
        SendMqService mqService = mock(SendMqService.class);
        QuotaService quotaService = mock(QuotaService.class);
        SendMqAction action = new SendMqAction();
        ReflectionTestUtils.setField(action, "sendMqService", mqService);
        ReflectionTestUtils.setField(action, "quotaService", quotaService);
        ReflectionTestUtils.setField(action, "sendMessageTopic", "topic");
        ReflectionTestUtils.setField(action, "tagId", "tag");
        ReflectionTestUtils.setField(action, "mqPipeline", "mock");
        doThrow(new RuntimeException("timeout")).when(mqService)
                .send(anyString(), anyString(), anyString());
        TaskInfo task = TaskInfo.builder().tenantId(7L).quotaReservationId(21L)
                .receiver(Collections.singleton("u1")).build();
        ProcessContext<SendTaskModel> context = ProcessContext.<SendTaskModel>builder()
                .needBreak(false).response(BasicResultVO.success())
                .processModel(SendTaskModel.builder().taskInfo(Collections.singletonList(task)).build()).build();

        action.process(context);

        assertTrue(context.getNeedBreak());
        verify(quotaService).markUnknown(7L, 21L);
    }
}
