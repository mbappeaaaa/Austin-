package com.java3y.austin.common.dto.account.sms;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 本地开发使用的模拟短信账号。该账号不包含任何第三方凭据。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MockSmsAccount extends SmsAccount {
}
