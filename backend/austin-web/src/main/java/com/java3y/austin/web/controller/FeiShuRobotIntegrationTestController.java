package com.java3y.austin.web.controller;

import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.dto.model.FeiShuRobotContentModel;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.common.enums.SendMessageType;
import com.java3y.austin.handler.domain.feishu.FeiShuRobotSendResult;
import com.java3y.austin.handler.handler.impl.FeiShuRobotHandler;
import com.java3y.austin.support.domain.ChannelAvailability;
import com.java3y.austin.support.service.EnvironmentFeiShuRobotAccountProvider;
import com.java3y.austin.web.annotation.AustinAspect;
import com.java3y.austin.web.annotation.AustinResult;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.service.FeiShuRobotTestAccessGuard;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飞书机器人连通性测试。仅 dev/local 注册，默认关闭，并要求独立管理员令牌。
 */
@AustinAspect
@AustinResult
@RestController
@RequestMapping("/feishu/robot/integration-test")
@Profile({"dev", "local"})
@ConditionalOnProperty(name = "austin.feishu.robot.test.enabled", havingValue = "true")
@Api("飞书机器人渠道集成测试")
public class FeiShuRobotIntegrationTestController {

    static final String FIXED_TEST_TEXT = "Austin 开发环境机器人连通性测试，请忽略。";

    private final FeiShuRobotHandler feiShuRobotHandler;
    private final EnvironmentFeiShuRobotAccountProvider accountProvider;
    private final FeiShuRobotTestAccessGuard accessGuard;

    public FeiShuRobotIntegrationTestController(FeiShuRobotHandler feiShuRobotHandler,
                                                 EnvironmentFeiShuRobotAccountProvider accountProvider,
                                                 FeiShuRobotTestAccessGuard accessGuard) {
        this.feiShuRobotHandler = feiShuRobotHandler;
        this.accountProvider = accountProvider;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/send")
    @ApiOperation("使用固定文本测试环境变量飞书群机器人")
    public Map<String, Object> send(HttpServletRequest request) {
        String adminToken = request.getHeader("X-Austin-Admin-Token");
        if (!accessGuard.isAdministrator(adminToken)) {
            throw new CommonException("无管理员权限，禁止调用飞书机器人测试接口");
        }
        ChannelAvailability availability = accountProvider.availability();
        if (!availability.isReady()) {
            throw new CommonException("飞书机器人渠道不可用：" + availability.getMessage());
        }

        FeiShuRobotContentModel content = FeiShuRobotContentModel.builder()
                .sendType(SendMessageType.TEXT.getCode())
                .content(FIXED_TEST_TEXT)
                .build();
        TaskInfo taskInfo = TaskInfo.builder()
                .sendChannel(ChannelType.FEI_SHU_ROBOT.getCode())
                .sendAccount(EnvironmentFeiShuRobotAccountProvider.ENVIRONMENT_FEISHU_ROBOT_ACCOUNT_ID)
                .contentModel(content)
                .build();
        FeiShuRobotSendResult sendResult = feiShuRobotHandler.sendWithResult(taskInfo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", sendResult.isSuccess());
        result.put("responseCode", sendResult.getResponseCode());
        result.put("errorReason", sendResult.getErrorReason());
        return result;
    }
}
