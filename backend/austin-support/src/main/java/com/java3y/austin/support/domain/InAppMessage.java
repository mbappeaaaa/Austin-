package com.java3y.austin.support.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * IM 站内信。删除只更新 isDeleted，保留审计与历史数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "in_app_message", indexes = {
        @Index(name = "idx_in_app_receiver_created", columnList = "receiver,is_deleted,created"),
        @Index(name = "idx_in_app_receiver_unread", columnList = "receiver,is_deleted,is_read"),
        @Index(name = "idx_in_app_tenant_receiver_created", columnList = "tenant_id,receiver,is_deleted,created")
}, uniqueConstraints = @UniqueConstraint(name = "uk_in_app_message_tenant_receiver",
        columnNames = {"tenant_id", "message_id", "receiver"}))
public class InAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 128)
    private String receiver;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4096)
    private String content;

    @Column(length = 1024)
    private String url;

    @Column(name = "message_template_id")
    private Long messageTemplateId;
    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "is_read")
    private Integer isRead;
    @Column(name = "read_at")
    private Integer readAt;
    @Column(name = "is_deleted")
    private Integer isDeleted;
    private Integer created;
    private Integer updated;
}
