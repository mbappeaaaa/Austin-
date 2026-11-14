package com.java3y.austin.web.service;

import com.java3y.austin.common.enums.AuditStatus;
import com.java3y.austin.common.enums.MessageStatus;
import com.java3y.austin.common.enums.TemplateType;
import com.java3y.austin.support.dao.MessageTemplateDao;
import com.java3y.austin.support.domain.MessageTemplate;
import com.java3y.austin.web.service.impl.MessageTemplateServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageTemplateServiceImplTest {

    @Test
    void shouldMergePartialUpdateWithoutClearingSystemFields() {
        MessageTemplateDao dao = mock(MessageTemplateDao.class);
        MessageTemplateChannelValidator validator = mock(MessageTemplateChannelValidator.class);
        MessageTemplateServiceImpl service = new MessageTemplateServiceImpl();
        ReflectionTestUtils.setField(service, "messageTemplateDao", dao);
        ReflectionTestUtils.setField(service, "messageTemplateChannelValidator", validator);

        MessageTemplate persisted = MessageTemplate.builder()
                .id(7L)
                .tenantId(3L)
                .name("旧名称")
                .auditStatus(AuditStatus.AUDIT_SUCCESS.getCode())
                .msgStatus(MessageStatus.STOP.getCode())
                .templateType(TemplateType.REALTIME.getCode())
                .creator("dev-user")
                .updator("system-updator")
                .auditor("system-auditor")
                .team("system-team")
                .proposer("旧业务方")
                .isDeleted(0)
                .created(100)
                .updated(100)
                .build();
        MessageTemplate request = MessageTemplate.builder()
                .id(7L)
                .name("新名称")
                .proposer("新业务方")
                .msgType(10)
                .shieldType(10)
                .templateType(TemplateType.REALTIME.getCode())
                .expectPushTime("0")
                .idType(50)
                .sendChannel(40)
                .sendAccount(-40)
                .msgContent("{\"title\":\"Austin\",\"content\":\"厉害了\"}")
                .build();

        when(dao.findByIdAndTenantId(7L, 3L)).thenReturn(Optional.of(persisted));
        when(dao.save(any(MessageTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageTemplate saved = service.saveOrUpdate(request, 3L);

        assertSame(persisted, saved);
        assertEquals("新名称", saved.getName());
        assertEquals(3L, saved.getTenantId());
        assertEquals("新业务方", saved.getProposer());
        assertEquals("dev-user", saved.getCreator());
        assertEquals("system-updator", saved.getUpdator());
        assertEquals("system-auditor", saved.getAuditor());
        assertEquals("system-team", saved.getTeam());
        assertEquals(100, saved.getCreated());
        assertEquals(0, saved.getIsDeleted());
        assertEquals(AuditStatus.WAIT_AUDIT.getCode(), saved.getAuditStatus());
        assertEquals(MessageStatus.INIT.getCode(), saved.getMsgStatus());
        verify(validator).validate(persisted);
        verify(dao).save(persisted);
    }
}
