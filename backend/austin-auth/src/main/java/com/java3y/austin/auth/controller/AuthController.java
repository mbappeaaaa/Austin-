package com.java3y.austin.auth.controller;

import com.java3y.austin.auth.security.AustinUserPrincipal;
import com.java3y.austin.auth.vo.AuthUserVo;
import com.java3y.austin.auth.vo.LoginRequest;
import com.java3y.austin.common.vo.BasicResultVO;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public BasicResultVO<AuthUserVo> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入用户名和密码");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername().trim(), request.getPassword()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            HttpSession session = servletRequest.getSession(true);
            servletRequest.changeSessionId();
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return BasicResultVO.success(AuthUserVo.from((AustinUserPrincipal) authentication.getPrincipal()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
    }

    @GetMapping("/me")
    public BasicResultVO<AuthUserVo> me(Authentication authentication) {
        return BasicResultVO.success(AuthUserVo.from((AustinUserPrincipal) authentication.getPrincipal()));
    }

    @PostMapping("/logout")
    public BasicResultVO<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return BasicResultVO.success();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
