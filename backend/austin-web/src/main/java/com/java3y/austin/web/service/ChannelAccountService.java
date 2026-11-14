package com.java3y.austin.web.service;


import com.java3y.austin.support.domain.ChannelAccount;

import java.util.List;

/**
 * 渠道账号接口
 *
 */
public interface ChannelAccountService {


    /**
     * 保存/修改渠道账号信息
     *
     * @param channelAccount
     * @return
     */
    ChannelAccount save(ChannelAccount channelAccount, Long tenantId);

    /** 恢复编辑请求中的脱敏占位符，并校验账号归属及渠道不可被偷换。 */
    ChannelAccount prepareForValidation(ChannelAccount channelAccount, Long tenantId);

    ChannelAccount queryById(Long id, Long tenantId);

    /**
     * 根据渠道标识查询账号信息
     *
     * @param channelType 渠道标识
     * @param creator     创建者
     * @return
     */
    List<ChannelAccount> queryByChannelType(Integer channelType, String creator, Long tenantId);

    List<ChannelAccount> queryByChannelType(Integer channelType, Long tenantId);


    /**
     * 列表信息
     *
     * @param creator
     * @return
     */
    List<ChannelAccount> list(String creator, Long tenantId);

    List<ChannelAccount> listAll(Long tenantId);

    /**
     * 软删除(deleted=1)
     *
     * @param ids
     */
    void deleteByIds(List<Long> ids, Long tenantId);

}
