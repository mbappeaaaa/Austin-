package com.java3y.austin.support.dao;

import com.java3y.austin.support.domain.InAppMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface InAppMessageDao extends JpaRepository<InAppMessage, Long>, JpaSpecificationExecutor<InAppMessage> {

    long countByTenantIdAndReceiverAndIsReadAndIsDeleted(
            Long tenantId, String receiver, Integer isRead, Integer isDeleted);

    boolean existsByTenantIdAndMessageIdAndReceiver(Long tenantId, String messageId, String receiver);

    @Modifying
    @Query(value = "INSERT INTO in_app_message "
            + "(tenant_id, receiver, title, content, url, message_template_id, business_id, message_id, "
            + "is_read, is_deleted, created, updated) VALUES "
            + "(:tenantId, :receiver, :title, :content, :url, :messageTemplateId, :businessId, "
            + ":messageId, 0, 0, :now, :now) ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
    int insertIgnore(@Param("tenantId") Long tenantId, @Param("receiver") String receiver,
                     @Param("title") String title, @Param("content") String content,
                     @Param("url") String url, @Param("messageTemplateId") Long messageTemplateId,
                     @Param("businessId") Long businessId, @Param("messageId") String messageId,
                     @Param("now") Integer now);

    @Modifying
    @Query("update InAppMessage m set m.isRead = 1, m.readAt = :now, m.updated = :now "
            + "where m.id = :id and m.tenantId = :tenantId and m.receiver = :receiver "
            + "and m.isDeleted = 0 and m.isRead = 0")
    int markRead(@Param("tenantId") Long tenantId, @Param("id") Long id,
                 @Param("receiver") String receiver, @Param("now") Integer now);

    @Modifying
    @Query("update InAppMessage m set m.isRead = 1, m.readAt = :now, m.updated = :now "
            + "where m.id in :ids and m.tenantId = :tenantId and m.receiver = :receiver "
            + "and m.isDeleted = 0 and m.isRead = 0")
    int markReadBatch(@Param("tenantId") Long tenantId, @Param("ids") Collection<Long> ids,
                      @Param("receiver") String receiver,
                      @Param("now") Integer now);

    @Modifying
    @Query("update InAppMessage m set m.isRead = 1, m.readAt = :now, m.updated = :now "
            + "where m.tenantId = :tenantId and m.receiver = :receiver and m.isDeleted = 0 and m.isRead = 0")
    int markAllRead(@Param("tenantId") Long tenantId, @Param("receiver") String receiver,
                    @Param("now") Integer now);

    @Modifying
    @Query("update InAppMessage m set m.isDeleted = 1, m.updated = :now "
            + "where m.id = :id and m.tenantId = :tenantId and m.receiver = :receiver and m.isDeleted = 0")
    int logicalDelete(@Param("tenantId") Long tenantId, @Param("id") Long id,
                      @Param("receiver") String receiver, @Param("now") Integer now);

    @Modifying
    @Query("update InAppMessage m set m.isDeleted = 1, m.updated = :now "
            + "where m.id in :ids and m.tenantId = :tenantId and m.receiver = :receiver and m.isDeleted = 0")
    int logicalDeleteBatch(@Param("tenantId") Long tenantId, @Param("ids") Collection<Long> ids,
                           @Param("receiver") String receiver,
                           @Param("now") Integer now);
}
