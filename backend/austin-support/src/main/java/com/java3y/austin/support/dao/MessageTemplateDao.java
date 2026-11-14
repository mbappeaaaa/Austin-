package com.java3y.austin.support.dao;

import com.java3y.austin.support.domain.MessageTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;


/**
 * 消息模板Dao
 *
 */
public interface MessageTemplateDao extends JpaRepository<MessageTemplate, Long>, JpaSpecificationExecutor<MessageTemplate> {


    /**
     * 查询 列表（分页)
     *
     * @param deleted  0：未删除 1：删除
     * @param pageable 分页对象
     * @return
     */
    List<MessageTemplate> findAllByIsDeletedEqualsOrderByUpdatedDesc(Integer deleted, Pageable pageable);


    /**
     * 统计未删除的条数
     *
     * @param deleted
     * @return
     */
    Long countByIsDeletedEquals(Integer deleted);

    Optional<MessageTemplate> findByIdAndTenantId(Long id, Long tenantId);

    List<MessageTemplate> findAllByIdInAndTenantId(List<Long> ids, Long tenantId);

    Long countByTenantIdAndIsDeletedEquals(Long tenantId, Integer deleted);

}
