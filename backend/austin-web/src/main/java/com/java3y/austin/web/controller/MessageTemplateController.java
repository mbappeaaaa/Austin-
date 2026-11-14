package com.java3y.austin.web.controller;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.google.common.base.Throwables;
import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.service.api.domain.MessageParam;
import com.java3y.austin.service.api.domain.SendRequest;
import com.java3y.austin.service.api.domain.SendResponse;
import com.java3y.austin.service.api.enums.BusinessCode;
import com.java3y.austin.service.api.service.RecallService;
import com.java3y.austin.service.api.service.SendService;
import com.java3y.austin.support.domain.MessageTemplate;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.service.MessageTemplateService;
import com.java3y.austin.web.service.MessageTemplateChannelValidator;
import com.java3y.austin.web.utils.Convert4Amis;
import com.java3y.austin.web.vo.MessageTemplateParam;
import com.java3y.austin.web.vo.MessageTemplateVo;
import com.java3y.austin.web.vo.amis.CommonAmisVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * 消息模板管理Controller
 *
 */
@Slf4j
@AustinAspect
@AustinResult
@RestController
@RequestMapping("/messageTemplate")
@Api("发送消息")
public class MessageTemplateController {

    @Autowired
    private MessageTemplateService messageTemplateService;

    @Autowired
    private SendService sendService;

    @Autowired
    private RecallService recallService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private MessageTemplateChannelValidator messageTemplateChannelValidator;

    @Value("${austin.business.upload.crowd.path}")
    private String dataPath;

    /**
     * 如果Id存在，则修改
     * 如果Id不存在，则保存
     */
    @PostMapping("/save")
    @ApiOperation("/保存数据")
    public MessageTemplate saveOrUpdate(@RequestBody MessageTemplate messageTemplate) {
        String username = currentUserService.username();
        if (messageTemplate.getId() == null) {
            messageTemplate.setCreator(username);
        } else {
            requireOwner(messageTemplate.getId(), username);
        }
        messageTemplate.setUpdator(username);
        return messageTemplateService.saveOrUpdate(messageTemplate, currentUserService.tenantId());
    }

    /**
     * 列表数据
     */
    @GetMapping("/list")
    @ApiOperation("/列表页")
    public MessageTemplateVo queryList(@Validated MessageTemplateParam messageTemplateParam) {
        messageTemplateParam.setCreator(currentUserService.isAdmin() ? null : currentUserService.username());
        Page<MessageTemplate> messageTemplates = messageTemplateService.queryList(
                messageTemplateParam, currentUserService.tenantId());
        List<Map<String, Object>> result = messageTemplates.toList().stream().map(template -> {
            Map<String, Object> row = Convert4Amis.flatSingleMap(template);
            com.java3y.austin.support.domain.ChannelAvailability availability =
                    messageTemplateChannelValidator.check(template);
            row.put("channelAvailabilityStatus", availability.getStatus());
            row.put("channelAvailabilityMessage", availability.getMessage());
            row.put("channelReady", availability.isReady());
            return row;
        }).collect(Collectors.toList());
        return MessageTemplateVo.builder().count(messageTemplates.getTotalElements()).rows(result).build();
    }

    /**
     * 根据Id查找
     */
    @GetMapping("query/{id}")
    @ApiOperation("/根据Id查找")
    public Map<String, Object> queryById(@PathVariable("id") Long id) {
        MessageTemplate template = requireOwner(id, currentUserService.username());
        Map<String, Object> result = Convert4Amis.flatSingleMap(template);
        com.java3y.austin.support.domain.ChannelAvailability availability =
                messageTemplateChannelValidator.check(template);
        result.put("channelAvailabilityStatus", availability.getStatus());
        result.put("channelAvailabilityMessage", availability.getMessage());
        result.put("channelReady", availability.isReady());
        return result;
    }

    /**
     * 根据Id复制
     */
    @PostMapping("copy/{id}")
    @ApiOperation("/根据Id复制")
    public void copyById(@PathVariable("id") Long id) {
        requireOwner(id, currentUserService.username());
        messageTemplateService.copy(id, currentUserService.tenantId());
    }


    /**
     * 根据Id删除
     * id多个用逗号分隔开
     */
    @DeleteMapping("delete/{id}")
    @ApiOperation("/根据Ids删除")
    public void deleteByIds(@PathVariable("id") String id) {
        if (CharSequenceUtil.isNotBlank(id)) {
            List<Long> idList = Arrays.stream(id.split(StrPool.COMMA)).map(Long::valueOf).collect(Collectors.toList());
            String username = currentUserService.username();
            for (Long templateId : idList) {
                requireOwner(templateId, username);
            }
            messageTemplateService.deleteByIds(idList, currentUserService.tenantId());
        }
    }


    /**
     * 测试发送接口
     */
    @PostMapping("test")
    @ApiOperation("/测试发送接口")
    public SendResponse test(@RequestBody MessageTemplateParam messageTemplateParam) {
        MessageTemplate messageTemplate = requireOwner(messageTemplateParam.getId(), currentUserService.username());
        messageTemplateChannelValidator.validate(messageTemplate);
        Map<String, String> variables = JSON.parseObject(messageTemplateParam.getMsgContent(), new TypeReference<Map<String, String>>() {});
        MessageParam messageParam = MessageParam.builder().receiver(messageTemplateParam.getReceiver()).variables(variables).build();
        SendRequest sendRequest = SendRequest.builder().tenantId(currentUserService.tenantId())
                .idempotencyKey("template-test:" + IdUtil.fastSimpleUUID())
                .code(BusinessCode.COMMON_SEND.getCode()).messageTemplateId(messageTemplateParam.getId())
                .messageParam(messageParam).build();
        SendResponse response = sendService.send(sendRequest);
        if (!Objects.equals(response.getCode(), RespStatusEnum.SUCCESS.getCode())) {
            throw new CommonException(response.getMsg());
        }
        return response;
    }

    /**
     * 获取需要测试的模板占位符，透出给Amis
     */
    @PostMapping("test/content")
    @ApiOperation("/测试发送接口")
    public CommonAmisVo test(Long id) {
        MessageTemplate messageTemplate = requireOwner(id, currentUserService.username());
        return Convert4Amis.getTestContent(messageTemplate.getMsgContent());
    }


    /**
     * 撤回接口（根据模板id撤回）
     */
    @PostMapping("recall/{id}")
    @ApiOperation("/撤回消息接口")
    public SendResponse recall(@PathVariable("id") String id) {
        requireOwner(Long.valueOf(id), currentUserService.username());
        SendRequest sendRequest = SendRequest.builder().tenantId(currentUserService.tenantId())
                .code(BusinessCode.RECALL.getCode()).messageTemplateId(Long.valueOf(id)).build();
        SendResponse response = recallService.recall(sendRequest);
        if (!Objects.equals(response.getCode(), RespStatusEnum.SUCCESS.getCode())) {
            throw new CommonException(response.getMsg());
        }
        return response;
    }


    /**
     * 启动模板的定时任务
     */
    @PostMapping("start/{id}")
    @ApiOperation("/启动模板的定时任务")
    public BasicResultVO start(@RequestBody @PathVariable("id") Long id) {
        requireOwner(id, currentUserService.username());
        return messageTemplateService.startCronTask(id, currentUserService.tenantId());
    }

    /**
     * 暂停模板的定时任务
     */
    @PostMapping("stop/{id}")
    @ApiOperation("/暂停模板的定时任务")
    public BasicResultVO stop(@RequestBody @PathVariable("id") Long id) {
        requireOwner(id, currentUserService.username());
        return messageTemplateService.stopCronTask(id, currentUserService.tenantId());
    }

    /**
     * 上传人群文件
     */
    @PostMapping("upload")
    @ApiOperation("/上传人群文件")
    public Map<Object, Object> upload(@RequestParam("file") MultipartFile file) {
        String filePath = dataPath + IdUtil.fastSimpleUUID() + file.getOriginalFilename();
        try {
            File localFile = new File(filePath);
            if (!localFile.exists()) {
                boolean res = localFile.mkdirs();
                if (!res) {
                    log.error("MessageTemplateController#upload fail! Failed to create folder.");
                    throw new CommonException(RespStatusEnum.SERVICE_ERROR);
                }
            }
            file.transferTo(localFile);
        } catch (Exception e) {
            log.error("MessageTemplateController#upload fail! e:{},params{}", Throwables.getStackTraceAsString(e), JSON.toJSONString(file));
            throw new CommonException(RespStatusEnum.SERVICE_ERROR);
        }
        return MapUtil.of(new String[][]{{"value", filePath}});
    }

    private MessageTemplate requireOwner(Long id, String username) {
        MessageTemplate template = messageTemplateService.queryById(id, currentUserService.tenantId());
        if (template == null || (!currentUserService.isAdmin()
                && !Objects.equals(template.getCreator(), username))) {
            throw new CommonException("消息模板不存在或无权访问");
        }
        return template;
    }

}
