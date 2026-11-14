package com.java3y.austin.service.api.impl.action.send;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReUtil;
import com.alibaba.fastjson2.JSON;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.IdType;
import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.pipeline.BusinessProcess;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.service.api.impl.domain.SendTaskModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 【消息链路 - api侧第3站：接收者过滤】（链路图"接收者过滤/组装"的"过滤"半段）
 *
 * 作用：部分渠道对接收者格式有硬性要求（短信必须是合法手机号、邮件必须是合法邮箱），
 *       不合法的接收者继续走链路只会在渠道侧失败且浪费额度，在这里按正则提前清洗。
 *
 * 实现：
 *       1. CHANNEL_REGEX_EXP 按 idType（手机号/邮箱）映射正则，static 初始化 + unmodifiableMap 防篡改；
 *       2. filterIllegalReceiver 取第一个 TaskInfo 的 idType——同一批接收者类型必须一致；
 *       3. filter 用 ReUtil.isMatch 逐个匹配，不合法的从 receiver 集合剔除并记 error 日志；
 *       4. 某个 TaskInfo 接收者被剔光 → 整个 TaskInfo 移除；全部剔光 → 终止链路返回错误。
 *
 */
@Slf4j
@Service
public class SendAfterCheckAction implements BusinessProcess<SendTaskModel> {

    public static final String PHONE_REGEX_EXP = "^((13[0-9])|(14[5,7,9])|(15[0-3,5-9])|(166)|(17[0-9])|(18[0-9])|(19[1,8,9]))\\d{8}$";
    public static final String EMAIL_REGEX_EXP = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    /**
     * 邮件和手机号正则
     */
    protected static final Map<Integer, String> CHANNEL_REGEX_EXP;

    static {
        Map<Integer, String> tempMap = new HashMap<>();
        tempMap.put(IdType.PHONE.getCode(), PHONE_REGEX_EXP);
        tempMap.put(IdType.EMAIL.getCode(), EMAIL_REGEX_EXP);
        // 初始化为不可变集合，避免被恶意修改
        CHANNEL_REGEX_EXP = Collections.unmodifiableMap(tempMap);
    }


    @Override
    public void process(ProcessContext<SendTaskModel> context) {
        SendTaskModel sendTaskModel = context.getProcessModel();
        List<TaskInfo> taskInfo = sendTaskModel.getTaskInfo();

        // 【过滤1】取第一个 TaskInfo 的 idType 决定用哪个正则——同一批接收者类型必须一致
        // 过滤掉不合法的手机号、邮件
        filterIllegalReceiver(taskInfo);
        // 【过滤2】全部接收者都不合法被剔光 → 终止链路返回错误
        if (CollUtil.isEmpty(taskInfo)) {
            context.setNeedBreak(true).setResponse(BasicResultVO.fail(RespStatusEnum.CLIENT_BAD_PARAMETERS, "手机号或邮箱不合法, 无有效的发送任务"));
        }

    }

    /**
     * 如果指定类型是手机号，检测输入手机号是否合法
     * 如果指定类型是邮件，检测输入邮件是否合法
     *
     * @param taskInfo
     */
    private void filterIllegalReceiver(List<TaskInfo> taskInfo) {
        Integer idType = CollUtil.getFirst(taskInfo.iterator()).getIdType();
        filter(taskInfo, CHANNEL_REGEX_EXP.get(idType));
    }

    /**
     * 利用正则过滤掉不合法的接收者
     *
     * @param taskInfo
     * @param regexExp
     */
    private void filter(List<TaskInfo> taskInfo, String regexExp) {
        Iterator<TaskInfo> iterator = taskInfo.iterator();
        while (iterator.hasNext()) {
            TaskInfo task = iterator.next();
            // 逐个正则匹配：不合法的收集后剔除并记 error 日志，接收者被剔光的 TaskInfo 整个移除
            Set<String> illegalPhone = task.getReceiver().stream()
                    .filter(phone -> !ReUtil.isMatch(regexExp, phone))
                    .collect(Collectors.toSet());

            if (CollUtil.isNotEmpty(illegalPhone)) {
                task.getReceiver().removeAll(illegalPhone);
                log.error("messageTemplateId:{} find illegal receiver!{}", task.getMessageTemplateId(), JSON.toJSONString(illegalPhone));
            }
            if (CollUtil.isEmpty(task.getReceiver())) {
                iterator.remove();
            }
        }
    }

}
