package com.java3y.austin.web.controller;


import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import com.java3y.austin.auth.service.CurrentUserService;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.support.domain.ChannelAccount;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.service.ChannelAccountAvailabilityService;
import com.java3y.austin.support.service.EnvironmentEmailAccountProvider;
import com.java3y.austin.support.service.EnvironmentFeiShuRobotAccountProvider;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.service.ChannelAccountService;
import com.java3y.austin.web.utils.Convert4Amis;
import com.java3y.austin.web.utils.SensitiveConfigUtils;
import com.java3y.austin.web.vo.ChannelAccountVo;
import com.java3y.austin.web.vo.amis.CommonAmisVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 渠道账号管理接口
 *
 */
@Slf4j
@AustinAspect
@AustinResult
@RestController
@RequestMapping("/account")
@Api("渠道账号管理接口")
public class ChannelAccountController {

    @Autowired
    private ChannelAccountService channelAccountService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private ChannelAccountAvailabilityService availabilityService;

    @Autowired
    private EnvironmentEmailAccountProvider environmentEmailAccountProvider;

    @Autowired
    private EnvironmentFeiShuRobotAccountProvider environmentFeiShuRobotAccountProvider;

    /**
     * 如果Id存在，则修改
     * 如果Id不存在，则保存
     */
    @PostMapping("/save")
    @ApiOperation("/保存数据")
    public ChannelAccountVo saveOrUpdate(@RequestBody ChannelAccount channelAccount) {
        if (channelAccount.getId() != null && currentUserService.isAdmin()) {
            ChannelAccount stored = channelAccountService.queryById(
                    channelAccount.getId(), currentUserService.tenantId());
            if (stored == null) {
                throw new CommonException("渠道账号不存在");
            }
            channelAccount.setCreator(stored.getCreator());
        } else {
            channelAccount.setCreator(currentUserService.username());
        }
        channelAccountService.prepareForValidation(channelAccount, currentUserService.tenantId());
        ChannelAvailability availability = availabilityService.evaluate(channelAccount);
        if (!availability.isReady()) {
            throw new CommonException("渠道账号不可用（" + availability.getStatus() + "）：" + availability.getMessage());
        }
        ChannelAccount saved = channelAccountService.save(channelAccount, currentUserService.tenantId());
        return toSafeVo(saved, availabilityService.evaluate(saved));
    }

    /**
     * 根据渠道标识查询渠道账号相关的信息
     */
    @GetMapping("/queryByChannelType")
    @ApiOperation("/根据渠道标识查询相关的记录")
    public List<CommonAmisVo> query(Integer channelType, String creator) {
        creator = resolveCreator(creator);

        List<ChannelAccount> channelAccounts = channelAccountService.queryByChannelType(
                channelType, creator, currentUserService.tenantId());
        Map<Long, ChannelAvailability> availabilityMap = new LinkedHashMap<>();
        for (ChannelAccount account : channelAccounts) {
            availabilityMap.put(account.getId(), availabilityService.evaluate(account));
        }
        ChannelAvailability autoAvailability = ChannelType.SMS.getCode().equals(channelType)
                ? availabilityService.checkSmsFlow(null, currentUserService.tenantId())
                : availabilityService.checkChannel(channelType, creator, currentUserService.tenantId());
        List<CommonAmisVo> result = Convert4Amis.getChannelAccountVo(channelAccounts, channelType, availabilityMap, autoAvailability);
        if (ChannelType.EMAIL.getCode().equals(channelType)) {
            ChannelAvailability environmentAvailability = environmentEmailAccountProvider.availability();
            result.add(0, CommonAmisVo.builder().label(EnvironmentEmailAccountProvider.ENVIRONMENT_EMAIL_ACCOUNT_NAME
                            + " · " + environmentAvailability.getStatus())
                    .value(String.valueOf(EnvironmentEmailAccountProvider.ENVIRONMENT_EMAIL_ACCOUNT_ID))
                    .availabilityStatus(environmentAvailability.getStatus())
                    .availabilityMessage(environmentAvailability.getMessage())
                    .disabled(!environmentAvailability.isReady()).build());
        }
        if (ChannelType.FEI_SHU_ROBOT.getCode().equals(channelType)) {
            ChannelAvailability environmentAvailability = environmentFeiShuRobotAccountProvider.availability();
            result.add(0, CommonAmisVo.builder()
                    .label(EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_NAME
                            + " · " + environmentAvailability.getStatus())
                    .value(String.valueOf(EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID))
                    .availabilityStatus(environmentAvailability.getStatus())
                    .availabilityMessage(environmentAvailability.getMessage())
                    .disabled(!environmentAvailability.isReady()).build());
        }
        return result;
    }

    /**
     * 查询指定账号（或 AUTO）的可用性。
     */
    @GetMapping("/availability")
    @ApiOperation("/渠道账号可用性")
    public ChannelAvailability availability(Integer channelType, Integer sendAccount, Integer msgType, String creator) {
        creator = resolveCreator(creator);
        if (ChannelType.SMS.getCode().equals(channelType)
                && (sendAccount == null || sendAccount <= 0)) {
            return availabilityService.checkSmsFlow(msgType);
        }
        return availabilityService.check(channelType, sendAccount, creator, currentUserService.tenantId());
    }

    /**
     * 所有的渠道账号信息
     */
    @GetMapping("/list")
    @ApiOperation("/渠道账号列表信息")
    public List<ChannelAccountVo> list(String creator) {
        List<ChannelAccount> accounts = currentUserService.isAdmin()
                ? channelAccountService.listAll(currentUserService.tenantId())
                : channelAccountService.list(currentUserService.username(), currentUserService.tenantId());
        return accounts.stream()
                .map(account -> toSafeVo(account, availabilityService.evaluate(account)))
                .collect(Collectors.toList());
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
            for (Long accountId : idList) {
                ChannelAccount account = channelAccountService.queryById(accountId, currentUserService.tenantId());
                if (account == null || (!currentUserService.isAdmin()
                        && !Objects.equals(account.getCreator(), username))) {
                    throw new CommonException("渠道账号不存在或无权访问");
                }
            }
            channelAccountService.deleteByIds(idList, currentUserService.tenantId());
        }
    }

    private ChannelAccountVo toSafeVo(ChannelAccount account, ChannelAvailability availability) {
        return ChannelAccountVo.builder()
                .id(account.getId()).name(account.getName()).sendChannel(account.getSendChannel())
                .accountConfig(SensitiveConfigUtils.mask(account.getAccountConfig()))
                .creator(account.getCreator()).created(account.getCreated()).updated(account.getUpdated())
                .availabilityStatus(availability.getStatus()).availabilityMessage(availability.getMessage())
                .qualificationRequired(availability.isQualificationRequired()).build();
    }

    private String resolveCreator(String requestedCreator) {
        if (currentUserService.isAdmin() && CharSequenceUtil.isNotBlank(requestedCreator)) {
            return requestedCreator;
        }
        return currentUserService.username();
    }

}
