package com.java3y.austin.auth.controller;

import com.java3y.austin.auth.security.AustinUserPrincipal;
import com.java3y.austin.auth.service.UserAdminService;
import com.java3y.austin.auth.vo.PasswordResetRequest;
import com.java3y.austin.auth.vo.RoleOptionVo;
import com.java3y.austin.auth.vo.UserAdminVo;
import com.java3y.austin.auth.vo.UserCreateRequest;
import com.java3y.austin.auth.vo.UserUpdateRequest;
import com.java3y.austin.common.vo.BasicResultVO;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/auth/users")
public class UserAdminController {
    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public BasicResultVO<List<UserAdminVo>> list(Authentication authentication) {
        AustinUserPrincipal principal = principal(authentication);
        return BasicResultVO.success(userAdminService.list(principal.getTenantId()));
    }

    @GetMapping("/roles")
    public BasicResultVO<List<RoleOptionVo>> roles() {
        return BasicResultVO.success(userAdminService.roles());
    }

    @PostMapping
    public BasicResultVO<UserAdminVo> create(@RequestBody UserCreateRequest request,
                                             Authentication authentication) {
        AustinUserPrincipal principal = principal(authentication);
        return BasicResultVO.success(userAdminService.create(request, principal.getTenantId()));
    }

    @PutMapping("/{id}")
    public BasicResultVO<UserAdminVo> update(@PathVariable Long id,
                                              @RequestBody UserUpdateRequest request,
                                              Authentication authentication) {
        AustinUserPrincipal principal = principal(authentication);
        return BasicResultVO.success(userAdminService.update(
                id, request, principal.getId(), principal.getTenantId()));
    }

    @PutMapping("/{id}/password")
    public BasicResultVO<Void> resetPassword(@PathVariable Long id,
                                              @RequestBody PasswordResetRequest request,
                                              Authentication authentication) {
        userAdminService.resetPassword(id, request, principal(authentication).getTenantId());
        return BasicResultVO.success();
    }

    private AustinUserPrincipal principal(Authentication authentication) {
        return (AustinUserPrincipal) authentication.getPrincipal();
    }
}
