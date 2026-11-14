package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.service.DashboardService;
import com.java3y.austin.web.vo.DashboardOverviewVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营总览大盘接口
 */
@Slf4j
@AustinAspect
@AustinResult
@RestController
@RequestMapping("/dashboard")
@Api("运营总览大盘")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping("/overview")
    @ApiOperation("运营总览：额度/渠道/模板/发送量真实聚合数据，支持按渠道筛选")
    public DashboardOverviewVo overview(@RequestParam(defaultValue = "today") String range,
                                        @RequestParam(required = false) Integer channel) {
        return dashboardService.overview(currentUserService.tenantId(), range, channel);
    }
}
