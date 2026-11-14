package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.billing.domain.QuotaLedgerEntry;
import com.java3y.austin.billing.domain.QuotaLedgerEntryType;
import com.java3y.austin.billing.service.QuotaService;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.vo.QuotaAccountVo;
import com.java3y.austin.web.vo.QuotaLedgerEntryVo;
import com.java3y.austin.web.vo.QuotaLedgerPageVo;
import com.java3y.austin.web.vo.QuotaThresholdUpdateVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.stream.Collectors;

@AustinAspect
@AustinResult
@RestController
@RequestMapping("/billing/quota")
@Api("租户消息额度")
public class QuotaController {
    private final QuotaService quotaService;
    private final CurrentUserService currentUserService;

    public QuotaController(QuotaService quotaService, CurrentUserService currentUserService) {
        this.quotaService = quotaService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @ApiOperation("查询当前租户额度余额与告警阈值")
    public QuotaAccountVo account() {
        return QuotaAccountVo.from(quotaService.requireAccount(currentUserService.tenantId()));
    }

    @GetMapping("/ledger")
    @ApiOperation("分页查询当前租户额度流水")
    public QuotaLedgerPageVo ledger(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int perPage,
                                    @RequestParam(required = false) String type) {
        Page<QuotaLedgerEntry> result = quotaService.pageLedger(
                currentUserService.tenantId(), parseEntryType(type), page, perPage);
        return QuotaLedgerPageVo.builder().count(result.getTotalElements())
                .rows(result.getContent().stream().map(QuotaLedgerEntryVo::from)
                        .collect(Collectors.toList())).build();
    }

    @PutMapping("/warning-threshold")
    @ApiOperation("配置当前租户额度告警阈值")
    public QuotaAccountVo updateWarningThreshold(@RequestBody QuotaThresholdUpdateVo request) {
        if (request == null || request.getWarningThreshold() == null) {
            throw new IllegalArgumentException("额度告警阈值不能为空");
        }
        return QuotaAccountVo.from(quotaService.updateWarningThreshold(
                currentUserService.tenantId(), request.getWarningThreshold()));
    }

    private QuotaLedgerEntryType parseEntryType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("PURCHASE".equals(normalized)) {
            return QuotaLedgerEntryType.CREDIT;
        }
        try {
            return QuotaLedgerEntryType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的额度流水类型: " + type);
        }
    }
}
