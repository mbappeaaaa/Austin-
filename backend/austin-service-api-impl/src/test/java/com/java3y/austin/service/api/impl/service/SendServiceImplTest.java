package com.java3y.austin.service.api.impl.service;

import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.pipeline.ProcessController;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.service.api.domain.BatchSendRequest;
import com.java3y.austin.service.api.domain.MessageParam;
import com.java3y.austin.service.api.domain.SendRequest;
import com.java3y.austin.service.api.impl.domain.SendTaskModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendServiceImplTest {

    private ProcessController processController;
    private SendServiceImpl service;

    @BeforeEach
    void setUp() {
        processController = mock(ProcessController.class);
        service = new SendServiceImpl();
        ReflectionTestUtils.setField(service, "processController", processController);
        when(processController.process(any())).thenAnswer(invocation -> {
            ProcessContext<?> context = invocation.getArgument(0);
            context.setResponse(BasicResultVO.success(Collections.emptyList()));
            return context;
        });
    }

    @Test
    void shouldCarryTenantAndIdempotencyKeyIntoSingleSendPipeline() {
        service.send(SendRequest.builder().tenantId(7L).idempotencyKey("request-1").code("send")
                .messageTemplateId(10L).messageParam(MessageParam.builder().receiver("u1").build()).build());

        SendTaskModel model = capturedModel();
        assertEquals(7L, model.getTenantId());
        assertEquals("request-1", model.getIdempotencyKey());
    }

    @Test
    void shouldCarryTenantAndIdempotencyKeyIntoBatchPipeline() {
        service.batchSend(BatchSendRequest.builder().tenantId(7L).idempotencyKey("batch-1").code("send")
                .messageTemplateId(10L)
                .messageParamList(Collections.singletonList(MessageParam.builder().receiver("u1").build()))
                .build());

        SendTaskModel model = capturedModel();
        assertEquals(7L, model.getTenantId());
        assertEquals("batch-1", model.getIdempotencyKey());
    }

    private SendTaskModel capturedModel() {
        ArgumentCaptor<ProcessContext> captor = ArgumentCaptor.forClass(ProcessContext.class);
        verify(processController).process(captor.capture());
        return (SendTaskModel) captor.getValue().getProcessModel();
    }
}
