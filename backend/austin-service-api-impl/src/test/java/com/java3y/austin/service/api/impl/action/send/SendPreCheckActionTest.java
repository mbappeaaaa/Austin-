package com.java3y.austin.service.api.impl.action.send;

import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.service.api.domain.MessageParam;
import com.java3y.austin.service.api.impl.domain.SendTaskModel;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SendPreCheckActionTest {

    private final SendPreCheckAction action = new SendPreCheckAction();

    @Test
    void shouldRejectInternalSendWithoutTenantContext() {
        ProcessContext<SendTaskModel> context = context(SendTaskModel.builder()
                .messageTemplateId(10L)
                .messageParamList(Collections.singletonList(MessageParam.builder().receiver("user-1").build()))
                .build());

        action.process(context);

        assertTrue(context.getNeedBreak());
        assertEquals(RespStatusEnum.CLIENT_BAD_PARAMETERS.getCode(), context.getResponse().getStatus());
        assertTrue(context.getResponse().getMsg().contains("租户ID"));
    }

    @Test
    void shouldAllowValidTenantScopedSend() {
        SendTaskModel model = SendTaskModel.builder()
                .tenantId(7L)
                .messageTemplateId(10L)
                .messageParamList(Collections.singletonList(MessageParam.builder().receiver("user-1").build()))
                .build();
        ProcessContext<SendTaskModel> context = context(model);

        action.process(context);

        assertFalse(context.getNeedBreak());
        assertEquals(1, model.getMessageParamList().size());
    }

    private ProcessContext<SendTaskModel> context(SendTaskModel model) {
        return ProcessContext.<SendTaskModel>builder()
                .processModel(model)
                .needBreak(false)
                .response(BasicResultVO.success())
                .build();
    }
}
