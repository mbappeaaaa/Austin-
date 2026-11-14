package com.java3y.austin.support.dao;


import com.java3y.austin.support.domain.ChannelAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 渠道账号信息 Dao
 *
 */
public interface ChannelAccountDao extends JpaRepository<ChannelAccount, Long> {


    /**
     * 查询 列表
     *
     * @param deleted     0：未删除 1：删除
     * @param channelType 渠道值
     * @param creator     创建者
     * @return
     */
    List<ChannelAccount> findAllByIsDeletedEqualsAndCreatorEqualsAndSendChannelEquals(Integer deleted, String creator, Integer channelType);

    /**
     * 查询 列表
     *
     * @param deleted     0：未删除 1：删除
     * @param channelType 渠道值
     * @return
     */
    List<ChannelAccount> findAllByIsDeletedEqualsAndSendChannelEquals(Integer deleted, Integer channelType);

    /**
     * 根据创建者检索相关的记录
     *
     * @param creator
     * @return
     */
    List<ChannelAccount> findAllByCreatorEquals(String creator);

    List<ChannelAccount> findAllByIsDeletedEqualsAndCreatorEquals(Integer deleted, String creator);

    List<ChannelAccount> findAllByIsDeletedEquals(Integer deleted);

    /**
     * 统计未删除的条数
     *
     * @param deleted
     * @return
     */
    Long countByIsDeletedEquals(Integer deleted);

    Optional<ChannelAccount> findByIdAndTenantId(Long id, Long tenantId);

    List<ChannelAccount> findAllByIdInAndTenantId(List<Long> ids, Long tenantId);

    List<ChannelAccount> findAllByTenantIdAndIsDeletedEqualsAndCreatorEqualsAndSendChannelEquals(
            Long tenantId, Integer deleted, String creator, Integer channelType);

    List<ChannelAccount> findAllByTenantIdAndIsDeletedEqualsAndSendChannelEquals(
            Long tenantId, Integer deleted, Integer channelType);

    List<ChannelAccount> findAllByTenantIdAndIsDeletedEqualsAndCreatorEquals(
            Long tenantId, Integer deleted, String creator);

    List<ChannelAccount> findAllByTenantIdAndIsDeletedEquals(Long tenantId, Integer deleted);

}
