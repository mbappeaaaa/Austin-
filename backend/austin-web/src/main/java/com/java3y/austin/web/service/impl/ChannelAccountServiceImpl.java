package com.java3y.austin.web.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.constant.CommonConstant;
import com.java3y.austin.support.dao.ChannelAccountDao;
import com.java3y.austin.support.domain.ChannelAccount;
import com.java3y.austin.web.service.ChannelAccountService;
import com.java3y.austin.web.utils.SensitiveConfigUtils;
import com.java3y.austin.web.exception.CommonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 */
@Service
public class ChannelAccountServiceImpl implements ChannelAccountService {

    @Autowired
    private ChannelAccountDao channelAccountDao;

    @Override
    public ChannelAccount prepareForValidation(ChannelAccount channelAccount, Long tenantId) {
        if (channelAccount == null || channelAccount.getId() == null) {
            if (channelAccount != null) {
                channelAccount.setTenantId(tenantId);
            }
            return channelAccount;
        }
        ChannelAccount stored = channelAccountDao.findByIdAndTenantId(channelAccount.getId(), tenantId)
                .orElseThrow(() -> new CommonException("渠道账号不存在"));
        if (!Objects.equals(stored.getCreator(), channelAccount.getCreator())
                || !Objects.equals(stored.getSendChannel(), channelAccount.getSendChannel())) {
            throw new CommonException("不能修改其他用户的渠道账号或变更账号所属渠道");
        }
        channelAccount.setAccountConfig(SensitiveConfigUtils.restoreMasked(
                channelAccount.getAccountConfig(), stored.getAccountConfig()));
        channelAccount.setCreated(stored.getCreated());
        channelAccount.setIsDeleted(stored.getIsDeleted());
        channelAccount.setTenantId(stored.getTenantId());
        return channelAccount;
    }

    @Override
    public ChannelAccount queryById(Long id, Long tenantId) {
        return channelAccountDao.findByIdAndTenantId(id, tenantId).orElse(null);
    }

    @Override
    public ChannelAccount save(ChannelAccount channelAccount, Long tenantId) {
        channelAccount.setTenantId(tenantId);
        if (Objects.isNull(channelAccount.getId())) {
            channelAccount.setCreated(Math.toIntExact(DateUtil.currentSeconds()));
            channelAccount.setIsDeleted(CommonConstant.FALSE);
        }
        channelAccount.setCreator(CharSequenceUtil.isBlank(channelAccount.getCreator()) ? AustinConstant.DEFAULT_CREATOR : channelAccount.getCreator());
        channelAccount.setUpdated(Math.toIntExact(DateUtil.currentSeconds()));
        return channelAccountDao.save(channelAccount);
    }

    @Override
    public List<ChannelAccount> queryByChannelType(Integer channelType, String creator, Long tenantId) {
        return channelAccountDao.findAllByTenantIdAndIsDeletedEqualsAndCreatorEqualsAndSendChannelEquals(
                tenantId, CommonConstant.FALSE, creator, channelType);
    }

    @Override
    public List<ChannelAccount> queryByChannelType(Integer channelType, Long tenantId) {
        return channelAccountDao.findAllByTenantIdAndIsDeletedEqualsAndSendChannelEquals(
                tenantId, CommonConstant.FALSE, channelType);
    }

    @Override
    public List<ChannelAccount> list(String creator, Long tenantId) {
        return channelAccountDao.findAllByTenantIdAndIsDeletedEqualsAndCreatorEquals(
                tenantId, CommonConstant.FALSE, creator);
    }

    @Override
    public List<ChannelAccount> listAll(Long tenantId) {
        return channelAccountDao.findAllByTenantIdAndIsDeletedEquals(tenantId, CommonConstant.FALSE);
    }

    @Override
    public void deleteByIds(List<Long> ids, Long tenantId) {
        Iterable<ChannelAccount> accounts = channelAccountDao.findAllByIdInAndTenantId(ids, tenantId);
        int now = Math.toIntExact(DateUtil.currentSeconds());
        accounts.forEach(account -> {
            account.setIsDeleted(CommonConstant.TRUE);
            account.setUpdated(now);
        });
        channelAccountDao.saveAll(accounts);
    }
}
