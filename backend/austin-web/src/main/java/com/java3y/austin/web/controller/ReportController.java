package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.service.ReportRequestService;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.vo.ReportPageVo;
import com.java3y.austin.web.vo.ReportVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

@AustinAspect
@AustinResult
@RestController
@RequestMapping("/billing/reports")
@Api("月度消息用量与账务报告")
public class ReportController {
    private final ReportRequestService requestService;
    private final CurrentUserService currentUserService;

    public ReportController(ReportRequestService requestService,
                            CurrentUserService currentUserService) {
        this.requestService = requestService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/monthly")
    @ApiOperation("异步申请一个已结束自然月的Word报告")
    public ReportVo requestMonthly(@RequestParam String month) {
        return ReportVo.from(requestService.requestMonthly(currentUserService.tenantId(), parseMonth(month)));
    }

    @GetMapping
    @ApiOperation("当前租户报告列表")
    public ReportPageVo list(@RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int perPage) {
        Page<ReportGenerationJob> result = requestService.list(
                currentUserService.tenantId(), page, perPage);
        return ReportPageVo.builder().count(result.getTotalElements())
                .rows(result.getContent().stream().map(ReportVo::from)
                        .collect(Collectors.toList())).build();
    }

    @GetMapping("/{id}")
    @ApiOperation("查询当前租户报告状态")
    public ReportVo get(@PathVariable Long id) {
        return ReportVo.from(requestService.requireTenantJob(currentUserService.tenantId(), id));
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("报告月份格式必须为yyyy-MM");
        }
    }
}
