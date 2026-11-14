package com.java3y.austin.handler.handler.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.java3y.austin.common.domain.RecallTaskInfo;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.dto.account.WebhookAccount;
import com.java3y.austin.common.dto.model.WebhookContentModel;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.handler.handler.BaseHandler;
import com.java3y.austin.support.utils.AccountUtils;
import com.java3y.austin.support.utils.WebhookSecurityUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通用 Webhook：固定 POST application/json，禁止跳转、系统代理和非公网目标。
 */
@Slf4j
@Service
public class WebhookHandler extends BaseHandler {

    private static final int MAX_BODY_BYTES = 256 * 1024;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .dns(new PublicOnlyDns()).proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS).build();

    private final AccountUtils accountUtils;

    public WebhookHandler(AccountUtils accountUtils) {
        this.accountUtils = accountUtils;
        this.channelCode = ChannelType.WEBHOOK.getCode();
    }

    @Override
    public boolean handler(TaskInfo taskInfo) {
        try {
            WebhookAccount account = accountUtils.getAccountById(taskInfo.getSendAccount(), WebhookAccount.class);
            WebhookContentModel content = (WebhookContentModel) taskInfo.getContentModel();
            if (account == null || content == null) {
                return false;
            }
            WebhookSecurityUtils.validateUrl(account.getWebhook());
            WebhookSecurityUtils.validateHeaders(account.getHeaders());
            String body = normalizeJsonBody(content.getBody());
            Request.Builder request = new Request.Builder().url(account.getWebhook())
                    .post(RequestBody.create(body, JSON_MEDIA_TYPE));
            if (account.getHeaders() != null) {
                for (Map.Entry<String, String> header : account.getHeaders().entrySet()) {
                    request.addHeader(header.getKey(), header.getValue());
                }
            }
            try (Response response = HTTP_CLIENT.newCall(request.build()).execute()) {
                return response.code() >= 200 && response.code() < 300;
            }
        } catch (Exception e) {
            log.error("WebhookHandler send failed, messageId:{}, type:{}", taskInfo.getMessageId(),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    private String normalizeJsonBody(String body) {
        if (body == null || body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Webhook JSON body is empty or too large");
        }
        Object parsed = JSON.parse(body);
        if (!(parsed instanceof JSONObject) && !(parsed instanceof JSONArray)) {
            throw new IllegalArgumentException("Webhook body must be a JSON object or array");
        }
        return JSON.toJSONString(parsed);
    }

    @Override
    public void recall(RecallTaskInfo recallTaskInfo) {
        // 外部 Webhook 不具备通用撤回协议。
    }

    private static class PublicOnlyDns implements Dns {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            return WebhookSecurityUtils.resolvePublic(hostname);
        }
    }
}
