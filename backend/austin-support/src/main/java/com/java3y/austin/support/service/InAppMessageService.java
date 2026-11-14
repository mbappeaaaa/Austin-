package com.java3y.austin.support.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.java3y.austin.common.constant.CommonConstant;
import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.dto.model.ImContentModel;
import com.java3y.austin.support.dao.InAppMessageDao;
import com.java3y.austin.support.domain.InAppMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class InAppMessageService {

    private final InAppMessageDao inAppMessageDao;

    public InAppMessageService(InAppMessageDao inAppMessageDao) {
        this.inAppMessageDao = inAppMessageDao;
    }

    @Transactional
    public int store(TaskInfo taskInfo) {
        ImContentModel content = (ImContentModel) taskInfo.getContentModel();
        if (content == null || taskInfo.getReceiver() == null) {
            return 0;
        }
        Long tenantId = taskInfo.getTenantId() == null
                ? AustinConstant.DEFAULT_TENANT_ID : taskInfo.getTenantId();
        return storeSystemMessage(tenantId, taskInfo.getReceiver(), content.getTitle(),
                content.getContent(), content.getUrl(), taskInfo.getMessageId(),
                taskInfo.getMessageTemplateId(), taskInfo.getBusinessId());
    }

    /**
     * 系统事件直达站内信的幂等入口。唯一键为 tenantId + messageId + receiver。
     */
    @Transactional
    public int storeSystemMessage(Long tenantId, Collection<String> receivers,
                                  String title, String content, String url, String messageId) {
        return storeSystemMessage(tenantId, receivers, title, content, url, messageId, null, null);
    }

    private int storeSystemMessage(Long tenantId, Collection<String> receivers,
                                   String title, String content, String url, String messageId,
                                   Long messageTemplateId, Long businessId) {
        requireSystemMessage(tenantId, receivers, title, content, url, messageId);
        int now = Math.toIntExact(DateUtil.currentSeconds());
        int stored = 0;
        for (String receiver : receivers) {
            if (CharSequenceUtil.isBlank(receiver)) {
                continue;
            }
            stored += inAppMessageDao.insertIgnore(tenantId, receiver, defaultText(title),
                    defaultText(content), url, messageTemplateId, businessId, messageId, now);
        }
        return stored;
    }

    public Page<InAppMessage> list(Long tenantId, String receiver, boolean unreadOnly, int page, int perPage) {
        PageRequest request = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(perPage, 1), 100),
                Sort.by(Sort.Direction.DESC, "created", "id"));
        return inAppMessageDao.findAll((Specification<InAppMessage>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.equal(root.get("receiver"), receiver));
            predicates.add(cb.equal(root.get("isDeleted"), CommonConstant.FALSE));
            if (unreadOnly) {
                predicates.add(cb.equal(root.get("isRead"), CommonConstant.FALSE));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, request);
    }

    public long unreadCount(Long tenantId, String receiver) {
        return inAppMessageDao.countByTenantIdAndReceiverAndIsReadAndIsDeleted(
                tenantId, receiver, CommonConstant.FALSE,
                CommonConstant.FALSE);
    }

    @Transactional
    public int markRead(Long tenantId, String receiver, Long id) {
        return inAppMessageDao.markRead(tenantId, id, receiver, now());
    }

    @Transactional
    public int markReadBatch(Long tenantId, String receiver, Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? 0
                : inAppMessageDao.markReadBatch(tenantId, ids, receiver, now());
    }

    @Transactional
    public int markAllRead(Long tenantId, String receiver) {
        return inAppMessageDao.markAllRead(tenantId, receiver, now());
    }

    @Transactional
    public int logicalDelete(Long tenantId, String receiver, Long id) {
        return inAppMessageDao.logicalDelete(tenantId, id, receiver, now());
    }

    @Transactional
    public int logicalDeleteBatch(Long tenantId, String receiver, Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? 0
                : inAppMessageDao.logicalDeleteBatch(tenantId, ids, receiver, now());
    }

    private int now() {
        return Math.toIntExact(DateUtil.currentSeconds());
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private void requireSystemMessage(Long tenantId, Collection<String> receivers,
                                      String title, String content, String url, String messageId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
        if (receivers == null || receivers.isEmpty()) {
            throw new IllegalArgumentException("站内信接收人不能为空");
        }
        if (CharSequenceUtil.isBlank(messageId) || messageId.length() > 64) {
            throw new IllegalArgumentException("站内信消息ID不能为空或过长");
        }
        if (title != null && title.length() > 200) {
            throw new IllegalArgumentException("站内信标题过长");
        }
        if (content != null && content.length() > 4096) {
            throw new IllegalArgumentException("站内信正文过长");
        }
        if (url != null && url.length() > 1024) {
            throw new IllegalArgumentException("站内信链接过长");
        }
    }
}
