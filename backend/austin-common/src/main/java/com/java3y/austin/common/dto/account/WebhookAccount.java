package com.java3y.austin.common.dto.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 通用 Webhook 账号。只允许 POST JSON；headers 可用于保存外部系统要求的鉴权头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookAccount {

    private String webhook;
    private Map<String, String> headers;
}
