package com.java3y.austin.auth.service;

import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import com.java3y.austin.auth.security.AuthRole;
import com.java3y.austin.auth.vo.PasswordResetRequest;
import com.java3y.austin.auth.vo.RoleOptionVo;
import com.java3y.austin.auth.vo.UserAdminVo;
import com.java3y.austin.auth.vo.UserCreateRequest;
import com.java3y.austin.auth.vo.UserUpdateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserAdminService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,64}$");

    private final SysUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(SysUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserAdminVo> list(Long tenantId) {
        validateTenantId(tenantId);
        return repository.findAllByTenantId(tenantId, Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(UserAdminVo::from)
                .collect(Collectors.toList());
    }

    public List<RoleOptionVo> roles() {
        List<RoleOptionVo> options = new ArrayList<>();
        options.add(new RoleOptionVo("系统管理员", AuthRole.ADMIN.name()));
        options.add(new RoleOptionVo("运营人员", AuthRole.OPERATOR.name()));
        options.add(new RoleOptionVo("审计人员", AuthRole.AUDITOR.name()));
        options.add(new RoleOptionVo("普通用户", AuthRole.USER.name()));
        return options;
    }

    @Transactional
    public UserAdminVo create(UserCreateRequest request, Long tenantId) {
        validateTenantId(tenantId);
        if (request == null || request.getUsername() == null
                || !USERNAME_PATTERN.matcher(request.getUsername().trim()).matches()) {
            throw badRequest("用户名需为 3-64 位字母、数字、点、下划线或短横线");
        }
        String username = request.getUsername().trim();
        validateDisplayName(request.getDisplayName());
        validatePassword(request.getPassword());
        AuthRole role = parseRole(request.getRole());
        if (repository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        int now = Math.toIntExact(Instant.now().getEpochSecond());
        SysUser saved = repository.save(SysUser.builder()
                .tenantId(tenantId)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName().trim())
                .role(role.name())
                .enabled(true)
                .created(now)
                .updated(now)
                .build());
        return UserAdminVo.from(saved);
    }

    @Transactional
    public UserAdminVo update(Long id, UserUpdateRequest request, Long actingUserId, Long tenantId) {
        SysUser user = find(id, tenantId);
        if (request == null) {
            throw badRequest("用户信息不能为空");
        }
        validateDisplayName(request.getDisplayName());
        AuthRole role = parseRole(request.getRole());
        if (request.getEnabled() == null) {
            throw badRequest("启用状态不能为空");
        }
        boolean removesAdmin = AuthRole.ADMIN.name().equals(user.getRole())
                && (!AuthRole.ADMIN.equals(role) || !request.getEnabled());
        if (user.getId().equals(actingUserId) && removesAdmin) {
            throw badRequest("不能停用自己或移除自己的管理员角色");
        }
        if (removesAdmin
                && repository.countByTenantIdAndRoleAndEnabledTrue(tenantId, AuthRole.ADMIN.name()) <= 1) {
            throw badRequest("系统至少需要保留一个启用的管理员");
        }
        user.setDisplayName(request.getDisplayName().trim());
        user.setRole(role.name());
        user.setEnabled(request.getEnabled());
        user.setUpdated(Math.toIntExact(Instant.now().getEpochSecond()));
        return UserAdminVo.from(repository.save(user));
    }

    @Transactional
    public void resetPassword(Long id, PasswordResetRequest request, Long tenantId) {
        if (request == null) {
            throw badRequest("密码不能为空");
        }
        validatePassword(request.getPassword());
        SysUser user = find(id, tenantId);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setUpdated(Math.toIntExact(Instant.now().getEpochSecond()));
        repository.save(user);
    }

    private SysUser find(Long id, Long tenantId) {
        if (id == null) {
            throw badRequest("用户 ID 不能为空");
        }
        validateTenantId(tenantId);
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private void validateTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw badRequest("租户身份无效");
        }
    }

    private AuthRole parseRole(String role) {
        try {
            return AuthRole.parse(role == null ? null : role.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private void validateDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty() || displayName.trim().length() > 64) {
            throw badRequest("显示名称长度需为 1-64 位");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw badRequest("密码长度需为 8-72 位");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
