package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.report.service.ReportAdminService;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.exception.CommonException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AustinAspect
@AustinResult
@RestController
@RequestMapping("/admin/reports")
@Api("报告失败任务管理")
public class ReportAdminController {
    private final ReportAdminService adminService;
    private final CurrentUserService currentUserService;

    @Value("${austin.report.lease-seconds:300}")
    private int leaseSeconds;

    public ReportAdminController(ReportAdminService adminService,
                                 CurrentUserService currentUserService) {
        this.adminService = adminService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{id}/replay")
    @ApiOperation("人工重试当前租户的一条失败终止报告")
    public boolean replay(@PathVariable Long id) {
        if (!currentUserService.isAdmin()) {
            throw new CommonException("仅租户管理员可以重试失败报告");
        }
        return adminService.replay(currentUserService.tenantId(), id, leaseSeconds);
    }
}
