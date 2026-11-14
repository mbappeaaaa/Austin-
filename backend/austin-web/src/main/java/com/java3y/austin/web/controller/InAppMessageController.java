package com.java3y.austin.web.controller;

import cn.hutool.core.text.CharSequenceUtil;
import com.java3y.austin.support.service.InAppMessageService;
import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.vo.InAppMessageBatchRequest;
import com.java3y.austin.web.vo.InAppMessagePageVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@AustinAspect
@AustinResult
@RestController
@RequestMapping("/inbox")
@Api("IM 站内信")
public class InAppMessageController {

    private final InAppMessageService inAppMessageService;
    private final CurrentUserService currentUserService;

    public InAppMessageController(InAppMessageService inAppMessageService, CurrentUserService currentUserService) {
        this.inAppMessageService = inAppMessageService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/messages")
    @ApiOperation("消息列表")
    public InAppMessagePageVo messages(@RequestParam String receiver,
                                       @RequestParam(defaultValue = "false") boolean unreadOnly,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int perPage) {
        receiver = currentUserService.username();
        Long tenantId = currentUserService.tenantId();
        Page<com.java3y.austin.support.domain.InAppMessage> result =
                inAppMessageService.list(tenantId, receiver, unreadOnly, page, perPage);
        return InAppMessagePageVo.builder().count(result.getTotalElements())
                .unreadCount(inAppMessageService.unreadCount(tenantId, receiver)).rows(result.getContent()).build();
    }

    @GetMapping("/unread-count")
    @ApiOperation("未读数")
    public long unreadCount(@RequestParam String receiver) {
        receiver = currentUserService.username();
        return inAppMessageService.unreadCount(currentUserService.tenantId(), receiver);
    }

    @PostMapping("/messages/{id}/read")
    @ApiOperation("标记单条已读")
    public int markRead(@PathVariable Long id, @RequestParam String receiver) {
        receiver = currentUserService.username();
        return inAppMessageService.markRead(currentUserService.tenantId(), receiver, id);
    }

    @PostMapping("/messages/read")
    @ApiOperation("批量标记已读；ids 为空时标记当前用户全部已读")
    public int markReadBatch(@RequestBody InAppMessageBatchRequest request) {
        requireRequest(request);
        request.setReceiver(currentUserService.username());
        return request.getIds() == null || request.getIds().isEmpty()
                ? inAppMessageService.markAllRead(currentUserService.tenantId(), request.getReceiver())
                : inAppMessageService.markReadBatch(
                currentUserService.tenantId(), request.getReceiver(), request.getIds());
    }

    @DeleteMapping("/messages/{id}")
    @ApiOperation("逻辑删除单条消息")
    public int delete(@PathVariable Long id, @RequestParam String receiver) {
        receiver = currentUserService.username();
        return inAppMessageService.logicalDelete(currentUserService.tenantId(), receiver, id);
    }

    @PostMapping("/messages/delete")
    @ApiOperation("批量逻辑删除消息")
    public int deleteBatch(@RequestBody InAppMessageBatchRequest request) {
        requireRequest(request);
        request.setReceiver(currentUserService.username());
        return inAppMessageService.logicalDeleteBatch(
                currentUserService.tenantId(), request.getReceiver(), request.getIds());
    }

    private void requireRequest(InAppMessageBatchRequest request) {
        if (request == null) {
            throw new CommonException("请求不能为空");
        }
        requireReceiver(request.getReceiver());
    }

    private void requireReceiver(String receiver) {
        if (CharSequenceUtil.isBlank(receiver) || receiver.length() > 128) {
            throw new CommonException("receiver 不能为空且长度不能超过 128");
        }
    }
}
