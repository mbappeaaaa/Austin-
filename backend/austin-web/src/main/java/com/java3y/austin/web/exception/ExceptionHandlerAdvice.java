package com.java3y.austin.web.exception;

import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.vo.BasicResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author kl
 * @version 1.0.0
 * @description 拦截异常统一返回
 * @date 2023/2/9 19:03
 */
@ControllerAdvice(basePackages = "com.java3y.austin.web.controller")
@ResponseBody
public class ExceptionHandlerAdvice {
    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlerAdvice.class);


    @ExceptionHandler({Exception.class})
    @ResponseStatus(HttpStatus.OK)
    public BasicResultVO<String> exceptionResponse(Exception e) {
        String errStackStr = stackTrace(e);
        log.error(errStackStr);
        return BasicResultVO.fail(RespStatusEnum.ERROR_500, "服务暂时不可用，请稍后重试或联系管理员");
    }

    @ExceptionHandler(com.java3y.austin.billing.payment.provider.PaymentProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public BasicResultVO<String> paymentProviderUnavailable(
            com.java3y.austin.billing.payment.provider.PaymentProviderUnavailableException e) {
        log.warn(e.getMessage());
        return BasicResultVO.fail(e.getMessage());
    }

    @ExceptionHandler({CommonException.class})
    @ResponseStatus(HttpStatus.OK)
    public BasicResultVO<RespStatusEnum> commonResponse(CommonException ce) {
        log.error(stackTrace(ce));
        return new BasicResultVO<>(ce.getCode(), ce.getMessage(), ce.getRespStatusEnum());
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
