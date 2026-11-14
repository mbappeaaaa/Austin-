package com.java3y.austin.support.service;

import cn.hutool.extra.mail.MailAccount;
import com.java3y.austin.common.enums.ChannelAvailabilityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentEmailAccountProviderTest {

    @Test
    void shouldBuildMailAccountFromEnvironmentMappedProperties() {
        EnvironmentEmailAccountProvider provider = new EnvironmentEmailAccountProvider(configuredEnvironment());

        MailAccount account = provider.getAccount();

        assertEquals("smtp.example.test", account.getHost());
        assertEquals(Integer.valueOf(465), account.getPort());
        assertEquals("sender@example.test", account.getUser());
        assertEquals("runtime-only-code", account.getPass());
        assertEquals("sender@example.test", account.getFrom());
        assertTrue(account.isSslEnable());
        assertFalse(account.isStarttlsEnable());
        assertEquals(ChannelAvailabilityStatus.READY, provider.availability().getStatus());
    }

    @Test
    void shouldReportMissingAuthorizationCodeWithoutExposingValues() {
        MockEnvironment environment = configuredEnvironment();
        environment.setProperty("austin.email.smtp.auth-code", "");

        String message = new EnvironmentEmailAccountProvider(environment).availability().getMessage();

        assertTrue(message.contains("AUSTIN_SMTP_AUTH_CODE"));
        assertFalse(message.contains("sender@example.test"));
    }

    private MockEnvironment configuredEnvironment() {
        return new MockEnvironment()
                .withProperty("austin.email.smtp.host", "smtp.example.test")
                .withProperty("austin.email.smtp.port", "465")
                .withProperty("austin.email.smtp.user", "sender@example.test")
                .withProperty("austin.email.smtp.from", "sender@example.test")
                .withProperty("austin.email.smtp.auth-code", "runtime-only-code")
                .withProperty("austin.email.smtp.ssl-enabled", "true")
                .withProperty("austin.email.smtp.starttls-enabled", "false")
                .withProperty("austin.email.test.to", "recipient@example.test");
    }
}
