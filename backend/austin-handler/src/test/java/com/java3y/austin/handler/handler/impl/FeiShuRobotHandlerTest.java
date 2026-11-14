package com.java3y.austin.handler.handler.impl;

import com.java3y.austin.common.dto.account.FeiShuRobotAccount;
import com.java3y.austin.handler.domain.feishu.FeiShuRobotResult;
import com.java3y.austin.support.service.EnvironmentFeiShuRobotAccountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeiShuRobotHandlerTest {

    @Test
    void shouldResolveEnvironmentAccountWithoutDatabase() {
        EnvironmentFeiShuRobotAccountProvider provider = mock(EnvironmentFeiShuRobotAccountProvider.class);
        FeiShuRobotAccount expected = FeiShuRobotAccount.builder().webhook("https://example.test/masked").build();
        when(provider.getAccount()).thenReturn(expected);
        FeiShuRobotHandler handler = new FeiShuRobotHandler();
        ReflectionTestUtils.setField(handler, "environmentFeiShuRobotAccountProvider", provider);

        FeiShuRobotAccount actual = ReflectionTestUtils.invokeMethod(handler, "getAccount",
                EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID);

        assertEquals(expected, actual);
    }

    @Test
    void shouldSupportNewFeiShuCodeFieldAndMaskSensitiveReasons() {
        FeiShuRobotHandler handler = new FeiShuRobotHandler();
        FeiShuRobotResult rawResult = FeiShuRobotResult.builder().code(19024).msg("failed").build();

        Integer code = ReflectionTestUtils.invokeMethod(handler, "effectiveResponseCode", rawResult);
        String masked = ReflectionTestUtils.invokeMethod(handler, "maskErrorReason",
                "webhook=https://open.feishu.cn/open-apis/bot/v2/hook/very-secret-token-value token=secret-value-12345678901234567890");

        assertEquals(Integer.valueOf(19024), code);
        assertFalse(masked.contains("very-secret-token-value"));
        assertFalse(masked.contains("secret-value-12345678901234567890"));
    }
}
