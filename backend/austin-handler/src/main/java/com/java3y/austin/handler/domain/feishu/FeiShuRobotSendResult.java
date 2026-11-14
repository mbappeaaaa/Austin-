package com.java3y.austin.handler.domain.feishu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 飞书机器人发送结果。仅保留可安全返回的响应码和脱敏原因。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeiShuRobotSendResult {

    private boolean success;
    private Integer responseCode;
    private String errorReason;
}
