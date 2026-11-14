package com.java3y.austin.auth.controller;

import com.java3y.austin.common.vo.BasicResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthExceptionHandlerAdviceTest {

    @Test
    void shouldExposeValidationReasonInStandardResponse() {
        AuthExceptionHandlerAdvice advice = new AuthExceptionHandlerAdvice();

        ResponseEntity<BasicResultVO<Void>> response = advice.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码长度需为 8-72 位"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("密码长度需为 8-72 位", response.getBody().getMsg());
    }
}
