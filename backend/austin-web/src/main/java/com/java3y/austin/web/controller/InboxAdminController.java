package com.java3y.austin.web.controller;

import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.handler.inbox.InboxAdminService;
import com.java3y.austin.handler.inbox.InboxMessage;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.vo.InboxDeadLetterPageVo;
import com.java3y.austin.web.vo.InboxDeadLetterVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
@RequestMapping("/admin/mq/inbox")
@Api("MQ死信管理")
public class InboxAdminController {

    private final InboxAdminService inboxAdminService;
    private final CurrentUserService currentUserService;

    public InboxAdminController(InboxAdminService inboxAdminService,
                                CurrentUserService currentUserService) {
        this.inboxAdminService = inboxAdminService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dead")
    @ApiOperation("当前租户死信列表；响应不返回消息payload")
    public InboxDeadLetterPageVo deadLetters(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int perPage) {
        requireAdmin();
        Page<InboxMessage> result = inboxAdminService.listDead(
                currentUserService.tenantId(), page, perPage);
        return InboxDeadLetterPageVo.builder().count(result.getTotalElements())
                .rows(result.getContent().stream().map(InboxDeadLetterVo::from)
                        .collect(Collectors.toList())).build();
    }

    @PostMapping("/{id}/replay")
    @ApiOperation("人工重放当前租户的一条死信")
    public boolean replay(@PathVariable Long id) {
        requireAdmin();
        return inboxAdminService.replay(currentUserService.tenantId(), id);
    }

    private void requireAdmin() {
        if (!currentUserService.isAdmin()) {
            throw new CommonException("仅租户管理员可以管理MQ死信");
        }
    }
}
