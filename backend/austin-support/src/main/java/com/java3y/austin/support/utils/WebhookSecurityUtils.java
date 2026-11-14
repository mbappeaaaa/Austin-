package com.java3y.austin.support.utils;

import cn.hutool.core.text.CharSequenceUtil;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Webhook SSRF 防护。保存配置和实际请求的 DNS 解析都必须调用这里，防止 DNS 重绑定绕过。
 */
public final class WebhookSecurityUtils {

    private static final List<String> FORBIDDEN_HEADERS = Arrays.asList(
            "host", "content-type", "content-length", "transfer-encoding", "connection", "proxy-authorization",
            "proxy-connection", "forwarded", "x-forwarded-for", "x-forwarded-host",
            "x-http-method-override", "x-method-override");

    private WebhookSecurityUtils() {
    }

    public static URI validateUrl(String webhook) {
        if (CharSequenceUtil.isBlank(webhook) || webhook.length() > 2048) {
            throw new IllegalArgumentException("Webhook URL 不能为空且长度不能超过 2048");
        }
        final URI uri;
        try {
            uri = new URI(webhook.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Webhook URL 格式无效");
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Webhook 仅支持 HTTPS，避免 URL 密钥和鉴权头明文传输");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Webhook URL 不允许包含用户信息或片段");
        }
        String host = normalizeHost(uri.getHost());
        if (CharSequenceUtil.isBlank(host) || isForbiddenHostName(host)) {
            throw new IllegalArgumentException("Webhook 主机不允许访问本机或内网");
        }
        try {
            validateAddresses(host, Arrays.asList(InetAddress.getAllByName(host)));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Webhook 主机无法解析");
        }
        return uri;
    }

    public static List<InetAddress> resolvePublic(String hostname) throws UnknownHostException {
        String host = normalizeHost(hostname);
        if (isForbiddenHostName(host)) {
            throw new UnknownHostException("Webhook host is not public");
        }
        List<InetAddress> addresses = Arrays.asList(InetAddress.getAllByName(host));
        validateAddresses(host, addresses);
        return addresses;
    }

    public static void validateHeaders(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        if (headers.size() > 32) {
            throw new IllegalArgumentException("Webhook 自定义请求头不能超过 32 个");
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            String value = entry.getValue();
            if (CharSequenceUtil.isBlank(name) || FORBIDDEN_HEADERS.contains(name)
                    || name.startsWith(":")) {
                throw new IllegalArgumentException("Webhook 包含不允许设置的请求头");
            }
            if (value == null || value.length() > 8192 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("Webhook 请求头值无效");
            }
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        try {
            return IDN.toASCII(normalized).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static boolean isForbiddenHostName(String host) {
        return CharSequenceUtil.isBlank(host) || "localhost".equals(host) || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".home.arpa");
    }

    private static void validateAddresses(String host, List<InetAddress> addresses) throws UnknownHostException {
        if (addresses.isEmpty()) {
            throw new UnknownHostException("Webhook host has no address");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new UnknownHostException("Webhook host resolved to a non-public address: " + host);
            }
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first != 0 && first != 127 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 192 && second == 0)
                    && !(first == 198 && (second == 18 || second == 19));
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) != 0xfc;
        }
        return false;
    }
}
