package com.java3y.austin.web.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.constant.CommonConstant;
import com.java3y.austin.common.enums.AuditStatus;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.common.enums.IdType;
import com.java3y.austin.common.enums.MessageStatus;
import com.java3y.austin.common.enums.RespStatusEnum;
import com.java3y.austin.common.enums.ShieldType;
import com.java3y.austin.common.enums.TemplateType;
import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.cron.xxl.entity.XxlJobInfo;
import com.java3y.austin.cron.xxl.service.CronTaskService;
import com.java3y.austin.cron.xxl.utils.XxlJobUtils;
import com.java3y.austin.support.dao.MessageTemplateDao;
import com.java3y.austin.support.domain.MessageTemplate;
import com.java3y.austin.web.exception.CommonException;
import com.java3y.austin.web.service.MessageTemplateService;
import com.java3y.austin.web.service.MessageTemplateChannelValidator;
import com.java3y.austin.web.vo.MessageTemplateParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 消息模板管理 Service
 *
 * @date 2022/1/22
 */
@Service
public class MessageTemplateServiceImpl implements MessageTemplateService {


    @Autowired
    private MessageTemplateDao messageTemplateDao;

    @Autowired
    private CronTaskService cronTaskService;

    @Autowired
    private XxlJobUtils xxlJobUtils;

    @Autowired
    private MessageTemplateChannelValidator messageTemplateChannelValidator;

    @Override
    public Page<MessageTemplate> queryList(MessageTemplateParam param, Long tenantId) {
        PageRequest pageRequest = PageRequest.of(param.getPage() - 1, param.getPerPage());
        String creator = param.getCreator();
        return messageTemplateDao.findAll((Specification<MessageTemplate>) (root, query, cb) -> {
            List<Predicate> predicateList = new ArrayList<>();
            predicateList.add(cb.equal(root.get("tenantId").as(Long.class), tenantId));
            // 加搜索条件
            if (CharSequenceUtil.isNotBlank(param.getKeywords())) {
                predicateList.add(cb.like(root.get("name").as(String.class), "%" + param.getKeywords() + "%"));
            }
            predicateList.add(cb.equal(root.get("isDeleted").as(Integer.class), CommonConstant.FALSE));
            if (CharSequenceUtil.isNotBlank(creator)) {
                predicateList.add(cb.equal(root.get("creator").as(String.class), creator));
            }
            Predicate[] p = new Predicate[predicateList.size()];
            // 查询
            query.where(cb.and(predicateList.toArray(p)));
            // 排序
            query.orderBy(cb.desc(root.get("updated")));
            return query.getRestriction();
        }, pageRequest);
    }

    @Override
    public Long count(Long tenantId) {
        return messageTemplateDao.countByTenantIdAndIsDeletedEquals(tenantId, CommonConstant.FALSE);
    }

    @Override
    public MessageTemplate saveOrUpdate(MessageTemplate messageTemplate, Long tenantId) {
        if (Objects.isNull(messageTemplate.getId())) {
            messageTemplate.setTenantId(tenantId);
            messageTemplateChannelValidator.validate(messageTemplate);
            initStatus(messageTemplate);
        } else {
            MessageTemplate persisted = queryById(messageTemplate.getId(), tenantId);
            if (Objects.isNull(persisted)) {
                throw new CommonException("消息模板不存在或已删除");
            }
            mergeEditableFields(persisted, messageTemplate);
            messageTemplate = persisted;
            messageTemplateChannelValidator.validate(messageTemplate);
            resetStatus(messageTemplate);
        }

        messageTemplate.setUpdated(Math.toIntExact(DateUtil.currentSeconds()));
        return messageTemplateDao.save(messageTemplate);
    }

    /**
     * 更新请求只包含页面上的可编辑字段，不能直接保存为一个完整实体，否则未提交的
     * auditor、creator、created 等系统字段会被 Hibernate 更新为 null。
     */
    private void mergeEditableFields(MessageTemplate target, MessageTemplate source) {
        if (Objects.nonNull(source.getName())) {
            target.setName(source.getName());
        }
        if (Objects.nonNull(source.getCronCrowdPath())) {
            target.setCronCrowdPath(source.getCronCrowdPath());
        }
        if (Objects.nonNull(source.getIdType())) {
            target.setIdType(source.getIdType());
        }
        if (Objects.nonNull(source.getSendChannel())) {
            target.setSendChannel(source.getSendChannel());
        }
        if (Objects.nonNull(source.getTemplateType())) {
            target.setTemplateType(source.getTemplateType());
        }
        if (Objects.nonNull(source.getShieldType())) {
            target.setShieldType(source.getShieldType());
        }
        if (Objects.nonNull(source.getMsgType())) {
            target.setMsgType(source.getMsgType());
        }
        if (Objects.nonNull(source.getExpectPushTime())) {
            target.setExpectPushTime(source.getExpectPushTime());
        }
        if (Objects.nonNull(source.getMsgContent())) {
            target.setMsgContent(source.getMsgContent());
        }
        if (Objects.nonNull(source.getSendAccount())) {
            target.setSendAccount(source.getSendAccount());
        }
        if (Objects.nonNull(source.getTeam())) {
            target.setTeam(source.getTeam());
        }
        if (Objects.nonNull(source.getProposer())) {
            target.setProposer(source.getProposer());
        }
    }


    @Override
    public void deleteByIds(List<Long> ids, Long tenantId) {
        Iterable<MessageTemplate> messageTemplates = messageTemplateDao.findAllByIdInAndTenantId(ids, tenantId);
        messageTemplates.forEach(messageTemplate -> messageTemplate.setIsDeleted(CommonConstant.TRUE));
        for (MessageTemplate messageTemplate : messageTemplates) {
            if (Objects.nonNull(messageTemplate.getCronTaskId()) && messageTemplate.getCronTaskId() > 0) {
                cronTaskService.deleteCronTask(messageTemplate.getCronTaskId());
            }
        }
        messageTemplateDao.saveAll(messageTemplates);
    }

    @Override
    public MessageTemplate queryById(Long id, Long tenantId) {
        return messageTemplateDao.findByIdAndTenantId(id, tenantId).orElse(null);
    }

    @Override
    public void copy(Long id, Long tenantId) {
        MessageTemplate messageTemplate = messageTemplateDao.findByIdAndTenantId(id, tenantId).orElse(null);
        if (Objects.nonNull(messageTemplate)) {
            MessageTemplate clone = ObjectUtil.clone(messageTemplate).setId(null).setCronTaskId(null);
            messageTemplateDao.save(clone);
        }
    }

    @Override
    public BasicResultVO startCronTask(Long id, Long tenantId) {
        // 1.获取消息模板的信息
        MessageTemplate messageTemplate = messageTemplateDao.findByIdAndTenantId(id, tenantId).orElse(null);
        if (Objects.isNull(messageTemplate)) {
            return BasicResultVO.fail();
        }
        messageTemplateChannelValidator.validate(messageTemplate);

        // 2.动态创建或更新定时任务
        XxlJobInfo xxlJobInfo = xxlJobUtils.buildXxlJobInfo(messageTemplate);

        // 3.获取taskId(如果本身存在则复用原有任务，如果不存在则得到新建后任务ID)
        Integer taskId = messageTemplate.getCronTaskId();
        BasicResultVO basicResultVO = cronTaskService.saveCronTask(xxlJobInfo);
        if (Objects.isNull(taskId) && RespStatusEnum.SUCCESS.getCode().equals(basicResultVO.getStatus()) && Objects.nonNull(basicResultVO.getData())) {
            taskId = Integer.valueOf(String.valueOf(basicResultVO.getData()));
        }

        // 4. 启动定时任务
        if (Objects.nonNull(taskId)) {
            cronTaskService.startCronTask(taskId);
            MessageTemplate clone = ObjectUtil.clone(messageTemplate).setMsgStatus(MessageStatus.RUN.getCode()).setCronTaskId(taskId).setUpdated(Math.toIntExact(DateUtil.currentSeconds()));
            messageTemplateDao.save(clone);
            return BasicResultVO.success();
        }
        return BasicResultVO.fail();
    }

    @Override
    public BasicResultVO stopCronTask(Long id, Long tenantId) {
        // 1.修改模板状态
        MessageTemplate messageTemplate = messageTemplateDao.findByIdAndTenantId(id, tenantId).orElse(null);
        if (Objects.isNull(messageTemplate)) {
            return BasicResultVO.fail();
        }
        MessageTemplate clone = ObjectUtil.clone(messageTemplate).setMsgStatus(MessageStatus.STOP.getCode()).setUpdated(Math.toIntExact(DateUtil.currentSeconds()));
        messageTemplateDao.save(clone);

        // 2.暂停定时任务
        return cronTaskService.stopCronTask(clone.getCronTaskId());
    }


    /**
     * 初始化状态信息
     *
     * @param messageTemplate
     */
    private void initStatus(MessageTemplate messageTemplate) {
        messageTemplate.setFlowId(CharSequenceUtil.EMPTY)
                .setMsgStatus(MessageStatus.INIT.getCode()).setAuditStatus(AuditStatus.WAIT_AUDIT.getCode())
                .setCreator(CharSequenceUtil.isBlank(messageTemplate.getCreator()) ? AustinConstant.DEFAULT_CREATOR : messageTemplate.getCreator())
                .setUpdator(CharSequenceUtil.isBlank(messageTemplate.getUpdator()) ? AustinConstant.DEFAULT_UPDATOR : messageTemplate.getUpdator())
                .setTeam(CharSequenceUtil.isBlank(messageTemplate.getTeam()) ? AustinConstant.DEFAULT_TEAM : messageTemplate.getTeam())
                .setAuditor(CharSequenceUtil.isBlank(messageTemplate.getAuditor()) ? AustinConstant.DEFAULT_AUDITOR : messageTemplate.getAuditor())
                .setCreated(Math.toIntExact(DateUtil.currentSeconds()))
                .setIsDeleted(CommonConstant.FALSE);

        // 前端编辑器不提交以下字段，而表列 NOT NULL：新建时统一补默认值，避免插入失败
        if (Objects.isNull(messageTemplate.getIdType())) {
            messageTemplate.setIdType(defaultIdType(messageTemplate.getSendChannel()));
        }
        if (Objects.isNull(messageTemplate.getShieldType())) {
            messageTemplate.setShieldType(ShieldType.NIGHT_NO_SHIELD.getCode());
        }
        if (Objects.isNull(messageTemplate.getCronCrowdPath())) {
            messageTemplate.setCronCrowdPath(CharSequenceUtil.EMPTY);
        }
        if (CharSequenceUtil.isBlank(messageTemplate.getProposer())) {
            messageTemplate.setProposer(messageTemplate.getCreator());
        }
    }

    /**
     * 按发送渠道推导默认的接收者 ID 类型（与 IdType 枚举语义保持一致）
     */
    private Integer defaultIdType(Integer sendChannel) {
        if (ChannelType.SMS.getCode().equals(sendChannel)) {
            return IdType.PHONE.getCode();
        }
        if (ChannelType.EMAIL.getCode().equals(sendChannel)) {
            return IdType.EMAIL.getCode();
        }
        if (ChannelType.PUSH.getCode().equals(sendChannel)) {
            return IdType.CID.getCode();
        }
        if (ChannelType.OFFICIAL_ACCOUNT.getCode().equals(sendChannel)
                || ChannelType.MINI_PROGRAM.getCode().equals(sendChannel)) {
            return IdType.OPEN_ID.getCode();
        }
        if (ChannelType.ENTERPRISE_WE_CHAT.getCode().equals(sendChannel)) {
            return IdType.ENTERPRISE_USER_ID.getCode();
        }
        if (ChannelType.DING_DING_WORK_NOTICE.getCode().equals(sendChannel)) {
            return IdType.DING_DING_USER_ID.getCode();
        }
        return IdType.USER_ID.getCode();
    }

    /**
     * 1. 重置模板的状态
     * 2. 修改定时任务信息(如果存在)
     *
     * @param messageTemplate
     */
    private void resetStatus(MessageTemplate messageTemplate) {
        messageTemplate.setUpdator(messageTemplate.getUpdator())
                .setMsgStatus(MessageStatus.INIT.getCode()).setAuditStatus(AuditStatus.WAIT_AUDIT.getCode());

        // 从数据库查询并注入 定时任务 ID
        MessageTemplate dbMsg = queryById(messageTemplate.getId(), messageTemplate.getTenantId());
        if (Objects.nonNull(dbMsg) && Objects.nonNull(dbMsg.getCronTaskId())) {
            messageTemplate.setCronTaskId(dbMsg.getCronTaskId());
        }

        if (Objects.nonNull(messageTemplate.getCronTaskId()) && TemplateType.CLOCKING.getCode().equals(messageTemplate.getTemplateType())) {
            XxlJobInfo xxlJobInfo = xxlJobUtils.buildXxlJobInfo(messageTemplate);
            cronTaskService.saveCronTask(xxlJobInfo);
            cronTaskService.stopCronTask(messageTemplate.getCronTaskId());
        }
    }


}
