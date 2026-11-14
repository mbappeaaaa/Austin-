package com.java3y.austin.support.utils;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSecurityUtilsTest {

    @Test
    void shouldRejectLocalPrivateMetadataAndCgnatAddresses() throws Exception {
        assertFalse(WebhookSecurityUtils.isPublicAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(WebhookSecurityUtils.isPublicAddress(InetAddress.getByName("10.0.0.8")));
        assertFalse(WebhookSecurityUtils.isPublicAddress(InetAddress.getByName("169.254.169.254")));
        assertFalse(WebhookSecurityUtils.isPublicAddress(InetAddress.getByName("100.64.0.1")));
        assertFalse(WebhookSecurityUtils.isPublicAddress(InetAddress.getByName("fc00::1")));
        assertTrue(WebhookSecurityUtils.isPublicAddress(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void shouldRejectLocalhostAndDangerousHeaders() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookSecurityUtils.validateUrl("http://localhost:8080/admin"));
        assertThrows(IllegalArgumentException.class,
                () -> WebhookSecurityUtils.validateHeaders(Collections.singletonMap("Host", "internal")));
        assertThrows(IllegalArgumentException.class,
                () -> WebhookSecurityUtils.validateHeaders(Collections.singletonMap("X-Test", "ok\r\nHost: bad")));
        assertThrows(IllegalArgumentException.class,
                () -> WebhookSecurityUtils.validateUrl("http://8.8.8.8/hook"));
    }
}
