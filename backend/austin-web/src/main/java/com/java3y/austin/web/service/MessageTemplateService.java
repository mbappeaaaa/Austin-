package com.java3y.austin.web.service;


import com.java3y.austin.common.vo.BasicResultVO;
import com.java3y.austin.support.domain.MessageTemplate;
import com.java3y.austin.web.vo.MessageTemplateParam;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 消息模板管理 接口
 *
 */
public interface MessageTemplateService {


    /**
     * 查询未删除的模板列表（分页)
     *
     * @param messageTemplateParam
     * @return
     */
    Page<MessageTemplate> queryList(MessageTemplateParam messageTemplateParam, Long tenantId);

    /**
     * 统计未删除的条数
     *
     * @return
     */
    Long count(Long tenantId);

    /**
     * 单个 保存或者更新
     * 存在ID 更新
     * 不存在ID保存
     *
     * @param messageTemplate
     * @return
     */
    MessageTemplate saveOrUpdate(MessageTemplate messageTemplate, Long tenantId);


    /**
     * 软删除(deleted=1)
     *
     * @param ids
     */
    void deleteByIds(List<Long> ids, Long tenantId);

    /**
     * 根据ID查询模板信息
     *
     * @param id
     * @return
     */
    MessageTemplate queryById(Long id, Long tenantId);

    /**
     * 复制配置
     *
     * @param id
     */
    void copy(Long id, Long tenantId);

    /**
     * 启动模板的定时任务
     *
     * @param id
     * @return
     */
    BasicResultVO startCronTask(Long id, Long tenantId);

    /**
     * 暂停模板的定时任务
     *
     * @param id
     * @return
     */
    BasicResultVO stopCronTask(Long id, Long tenantId);

}
