package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.billing.outbox.OutboxAdminService;
import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.vo.OutboxDeadLetterPageVo;
import com.java3y.austin.web.vo.OutboxDeadLetterVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@AustinAspect
@AustinResult
@RestController
@RequestMapping("/admin/billing/outbox")
@Api("账务Outbox死信管理")
public class OutboxAdminController {
    private final OutboxAdminService adminService;
    private final CurrentUserService currentUserService;

    @Value("${austin.billing.outbox.lease-seconds:60}")
    private int leaseSeconds;

    public OutboxAdminController(OutboxAdminService adminService,
                                 CurrentUserService currentUserService) {
        this.adminService = adminService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dead")
    @ApiOperation("当前租户账务Outbox死信列表；响应不返回事件payload")
    public OutboxDeadLetterPageVo deadLetters(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int perPage) {
        requireAdmin();
        Page<OutboxEvent> result = adminService.listDead(currentUserService.tenantId(), page, perPage);
        return OutboxDeadLetterPageVo.builder().count(result.getTotalElements())
                .rows(result.getContent().stream().map(OutboxDeadLetterVo::from)
                        .collect(Collectors.toList())).build();
    }

    @PostMapping("/{id}/replay")
    @ApiOperation("人工重放当前租户的一条账务Outbox死信")
    public boolean replay(@PathVariable Long id) {
        requireAdmin();
        return adminService.replay(currentUserService.tenantId(), id, leaseSeconds);
    }

    private void requireAdmin() {
        if (!currentUserService.isAdmin()) {
            throw new CommonException("仅租户管理员可以管理账务Outbox死信");
        }
    }
}
