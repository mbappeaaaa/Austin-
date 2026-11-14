package com.java3y.austin.handler.handler.impl;

import cn.hutool.extra.mail.MailAccount;
import com.java3y.austin.support.utils.AccountUtils;
import com.java3y.austin.support.service.EnvironmentEmailAccountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailHandlerTest {

    @Test
    void shouldUseDefaultTrustStoreAndVerifyServerIdentity() {
        AccountUtils accountUtils = mock(AccountUtils.class);
        MailAccount source = new MailAccount().setAuth(true).setSslEnable(true).setStarttlsEnable(false);
        when(accountUtils.getAccountById(1, MailAccount.class)).thenReturn(source);
        EmailHandler handler = new EmailHandler();
        ReflectionTestUtils.setField(handler, "accountUtils", accountUtils);

        MailAccount account = ReflectionTestUtils.invokeMethod(handler, "getAccountConfig", 1);

        assertEquals("true", account.getCustomProperty().get("mail.smtp.ssl.checkserveridentity"));
        assertFalse(account.getCustomProperty().containsKey("mail.smtp.ssl.socketFactory"));
        assertFalse(account.getCustomProperty().containsKey("mail.smtp.ssl.trust"));
    }

    @Test
    void shouldUseEnvironmentAccountForVirtualAccountId() {
        EnvironmentEmailAccountProvider provider = mock(EnvironmentEmailAccountProvider.class);
        MailAccount source = new MailAccount().setHost("smtp.example.test").setPort(465)
                .setUser("sender@example.test").setPass("runtime-code").setFrom("sender@example.test")
                .setAuth(true).setSslEnable(true).setStarttlsEnable(false);
        when(provider.getAccount()).thenReturn(source);
        EmailHandler handler = new EmailHandler();
        ReflectionTestUtils.setField(handler, "environmentEmailAccountProvider", provider);

        MailAccount account = ReflectionTestUtils.invokeMethod(handler, "getAccountConfig",
                EnvironmentEmailAccountProvider.ENVIRONMENT_EMAIL_ACCOUNT_ID);

        assertEquals("smtp.example.test", account.getHost());
        assertEquals("true", account.getCustomProperty().get("mail.smtp.ssl.checkserveridentity"));
    }
}
