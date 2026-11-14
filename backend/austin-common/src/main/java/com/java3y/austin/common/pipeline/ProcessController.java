package com.java3y.austin.common.pipeline;


import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.vo.BasicResultVO;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 流程控制器
 *
 */
@Data
public class ProcessController {

    /**
     * 模板映射
     */
    private Map<String, ProcessTemplate> templateConfig = null;


    /**
     * 执行责任链
     *
     * @param context
     * @return 返回上下文内容
     */
    public ProcessContext process(ProcessContext context) {

        /**
         * 前置检查
         */
        try {
            preCheck(context);
        } catch (ProcessException e) {
            return e.getProcessContext();
        }

        /**
         * 遍历流程节点
         */
        List<BusinessProcess> processList = templateConfig.get(context.getCode()).getProcessList();
        for (BusinessProcess businessProcess : processList) {
            businessProcess.process(context);
            if (Boolean.TRUE.equals(context.getNeedBreak())) {
                break;
            }
        }
        return context;
    }


    /**
     * 执行前检查，出错则抛出异常
     *
     * @param context 执行上下文
     * @throws ProcessException 异常信息
     */
    private void preCheck(ProcessContext context) throws ProcessException {
        // 上下文
        //context 是否为空,调用方传了 null
        if (Objects.isNull(context)) {
            context = new ProcessContext();
            context.setResponse(BasicResultVO.fail(RespStatusEnum.CONTEXT_IS_NULL));
            throw new ProcessException(context);
        }
        // 业务代码
        //context.getCode() 是否为空,请求体里没带 code（比如用户调 /send 时 JSON 里漏了 "code": "send"）
        String businessCode = context.getCode();
        if (Objects.isNull(businessCode)) {
            context.setResponse(BasicResultVO.fail(RespStatusEnum.BUSINESS_CODE_IS_NULL));
            throw new ProcessException(context);
        }
        // 执行模板
        //templateConfig.get(code) 能否查到模板
        //传了 code 但没有注册对应的流程（比如传了个乱写的 code "xxx"），此时 Map 返回 null
        ProcessTemplate processTemplate = templateConfig.get(businessCode);
        if (Objects.isNull(processTemplate)) {
            context.setResponse(BasicResultVO.fail(RespStatusEnum.PROCESS_TEMPLATE_IS_NULL));
            throw new ProcessException(context);
        }
        // 执行模板列表
        //模板里的 processList 是否为空,流程注册了但没配节点，属于配置错误
        List<BusinessProcess> processList = processTemplate.getProcessList();
        if (Objects.isNull(processList) || processList.isEmpty()) {
            context.setResponse(BasicResultVO.fail(RespStatusEnum.PROCESS_LIST_IS_NULL));
            throw new ProcessException(context);
        }
    }


}
