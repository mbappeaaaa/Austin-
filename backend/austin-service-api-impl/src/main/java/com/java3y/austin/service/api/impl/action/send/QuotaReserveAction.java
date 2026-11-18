package com.java3y.austin.service.api.impl.action.send;

import com.java3y.austin.billing.domain.QuotaReservation;
import com.java3y.austin.billing.domain.QuotaReservationResult;
import com.java3y.austin.billing.exception.InsufficientQuotaException;
import com.java3y.austin.billing.service.QuotaService;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.pipeline.BusinessProcess;
import com.java3y.austin.common.pipeline.ProcessContext;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.service.api.impl.domain.SendTaskModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 【消息链路 - api侧第2站：配额预占】（对应日志 [austin链路][1-配额预占]）
 *
 * 作用：计费三阶段（预占-结算-释放）的第一阶段。发送前先"冻结"额度防止渠道超发后账算不回来；
 *       同时靠幂等键识别重复请求（重复点击/接口重试），第二次直接返回"已受理"，避免重复扣量。
 *
 * 实现：
 *       1. 由 austin.billing.quota-enforcement-enabled 控制，false 时直接放行（跳过计费）；
 *       2. 幂等键为空直接拒绝——没有幂等键就无法防重复，宁可拒绝；
 *       3. 每个 TaskInfo 按接收人数调 QuotaService.reserveWithResult 预占，
 *          reservationKey = 幂等键:下标；同 key 重复请求 result.isCreated()=false → 幂等返回；
 *       4. 配额不足或任何异常时，把已预占成功的逐个 release 回滚（补偿），
 *          保证"要么全部预占成功、要么一个都不占"的原子效果。
 */
@Slf4j
@Service
public class QuotaReserveAction implements BusinessProcess<SendTaskModel> {

    private final QuotaService quotaService;
    //1. 由 austin.billing.quota-enforcement-enabled 控制，false 时直接放行（跳过计费）；
//    false	跳过整个配额检查——随便发，不扣额度（开源原版行为，也是项目默认值，这就是你最初疑惑“为什么发消息额度不变”的原因）
//    true	发送前必须预占额度：余额够才放行，不够直接拒绝（返回“额度不足”）；你本地 dev 配置里开的就是 true
    @Value("${austin.billing.quota-enforcement-enabled:false}")
    private boolean quotaEnforcementEnabled;

    public QuotaReserveAction(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Override
    public void process(ProcessContext<SendTaskModel> context) {
        // 【预占1】计费开关：quota-enforcement-enabled=false 时直接放行（跳过计费）
        if (!quotaEnforcementEnabled) {
            return;
        }
        SendTaskModel model = context.getProcessModel();
        // 【预占2】没有幂等键就无法防重复，宁可拒绝
//        预占时带的`reservationKey = 幂等键:下标` ，作用是 同一个请求重试不会重复预占
//        ——网络超时后业务方重发同一笔请求，第二次预占会命中幂等键直接返回“已受理”，额度不会被扣两次。这就是注释里“幂等键防重复”的含义。
        if (model.getIdempotencyKey() == null || model.getIdempotencyKey().trim().isEmpty()) {
            context.setNeedBreak(true).setResponse(BasicResultVO.fail(
                    RespStatusEnum.CLIENT_BAD_PARAMETERS, "发送请求幂等键不能为空"));
            return;
        }
        List<QuotaReservation> created = new ArrayList<>();
        try {
            // 【预占3】每个 TaskInfo 按接收人数预占，reservationKey = 幂等键:下标；
            // 同 key 重复请求 isCreated()=false → 幂等返回"已受理"
            for (int index = 0; index < model.getTaskInfo().size(); index++) {
                TaskInfo task = model.getTaskInfo().get(index);
                //计费对象不是“谁点了发送”，而是“这条消息要送到多少人手上”,`receiver` 参数是逗号分隔的字符串，拆开就是一组人
                long amount = task.getReceiver().size();
                String reservationKey = model.getIdempotencyKey() + ":" + index;
                QuotaReservationResult result = quotaService.reserveWithResult(model.getTenantId(), amount,
                        reservationKey, "MESSAGE", model.getMessageTemplateId() + ":" + index);
                if (!result.isCreated()) {
                    releaseCreated(model.getTenantId(), created);
                    context.setNeedBreak(true).setResponse(BasicResultVO.fail(
                            RespStatusEnum.IDEMPOTENT_REQUEST_ACCEPTED));
                    return;
                }
                task.setQuotaReservationId(result.getReservation().getId());
                created.add(result.getReservation());
            }
            log.info("[austin链路][1-配额预占] 预占成功 tenantId:{}, 模板:{}, 预占份数:{}",
                    model.getTenantId(), model.getMessageTemplateId(), created.size());
        // 【预占4】配额不足或异常：把已预占成功的逐个 release 回滚（补偿）——"要么全部预占成功、要么一个都不占"
        } catch (InsufficientQuotaException exception) {
            releaseCreated(model.getTenantId(), created);
            context.setNeedBreak(true).setResponse(BasicResultVO.fail(RespStatusEnum.QUOTA_INSUFFICIENT));
        } catch (RuntimeException exception) {
            releaseCreated(model.getTenantId(), created);
            context.setNeedBreak(true).setResponse(BasicResultVO.fail(RespStatusEnum.SERVICE_ERROR));
            log.error("reserve quota failed, tenantId:{}, type:{}",
                    model.getTenantId(), exception.getClass().getSimpleName());
        }
    }

    /**
     *配额不足或异常：把已预占成功的逐个 release 回滚（补偿）——"要么全部预占成功、要么一个都不占"
     * @param tenantId
     * @param reservations
     */
    private void releaseCreated(Long tenantId, List<QuotaReservation> reservations) {
        for (QuotaReservation reservation : reservations) {
            try {
                quotaService.release(tenantId, reservation.getId());
            } catch (RuntimeException compensationFailure) {
                log.error("release reserved quota failed, tenantId:{}, reservationId:{}, type:{}",
                        tenantId, reservation.getId(), compensationFailure.getClass().getSimpleName());
            }
        }
    }
}
