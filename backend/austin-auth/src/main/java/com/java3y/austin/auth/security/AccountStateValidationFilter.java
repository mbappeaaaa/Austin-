package com.java3y.austin.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import com.java3y.austin.common.vo.BasicResultVO;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;

/**
 * 每次请求校验数据库中的账号状态，使停用、角色变更和密码重置立即令旧 Session 失效。
 */
@Component
public class AccountStateValidationFilter extends OncePerRequestFilter {
    private final SysUserRepository repository;
    private final ObjectMapper objectMapper;

    public AccountStateValidationFilter(SysUserRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AustinUserPrincipal)) {
            filterChain.doFilter(request, response);
            return;
        }

        AustinUserPrincipal principal = (AustinUserPrincipal) authentication.getPrincipal();
        SysUser current = repository.findById(principal.getId()).orElse(null);
        if (isUnchangedAndEnabled(current, principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), BasicResultVO.fail("账号状态或权限已变更，请重新登录"));
    }

    private boolean isUnchangedAndEnabled(SysUser current, AustinUserPrincipal principal) {
        return current != null && Boolean.TRUE.equals(current.getEnabled())
                && Objects.equals(current.getUsername(), principal.getUsername())
                && Objects.equals(current.getTenantId(), principal.getTenantId())
                && Objects.equals(current.getPasswordHash(), principal.getPassword())
                && Objects.equals(current.getRole(), principal.getRole());
    }
}
