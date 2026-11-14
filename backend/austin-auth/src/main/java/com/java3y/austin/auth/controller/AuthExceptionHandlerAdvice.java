package com.java3y.austin.auth.controller;

import com.java3y.austin.common.vo.BasicResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 认证与用户管理接口的统一错误响应，确保前端能拿到可读的 msg。
 */
@RestControllerAdvice(basePackages = "com.java3y.austin.auth.controller")
public class AuthExceptionHandlerAdvice {
    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandlerAdvice.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<BasicResultVO<Void>> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "请求处理失败" : exception.getReason();
        return ResponseEntity.status(exception.getStatus()).body(BasicResultVO.fail(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BasicResultVO<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BasicResultVO.fail("请求数据格式不正确"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<BasicResultVO<Void>> handleConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(BasicResultVO.fail("用户名已存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BasicResultVO<Void>> handleUnexpected(Exception exception) {
        log.error("Authentication request failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BasicResultVO.fail("服务暂时不可用，请稍后重试"));
    }
}
