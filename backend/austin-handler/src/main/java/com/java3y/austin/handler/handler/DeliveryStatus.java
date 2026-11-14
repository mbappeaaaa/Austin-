package com.java3y.austin.handler.handler;

/**
 * 渠道调用的业务结果。它与 MQ 技术消费状态分离，防止“代码未抛异常”被误认为已经送达。
 */
public enum DeliveryStatus {
    SUCCESS,
    FINAL_FAILURE,
    RETRYABLE_FAILURE,
    UNKNOWN,
    SKIPPED,
    DEFERRED
}
