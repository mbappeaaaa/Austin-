package com.java3y.austin.report.service;

import com.java3y.austin.billing.payment.domain.OutboxEvent;
import com.java3y.austin.billing.payment.repository.OutboxEventRepository;
import com.java3y.austin.report.domain.ReportGenerationJob;
import com.java3y.austin.report.domain.ReportStatus;
import com.java3y.austin.report.domain.ReportType;
import com.java3y.austin.report.repository.ReportGenerationJobRepository;
import com.java3y.austin.report.storage.StoredReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportJobStateServiceTest {
    private ReportGenerationJobRepository jobs;
    private OutboxEventRepository outbox;
    private ReportJobStateService service;
    private ReportGenerationJob job;

    @BeforeEach
    void setUp() {
        jobs = mock(ReportGenerationJobRepository.class);
        outbox = mock(OutboxEventRepository.class);
        service = new ReportJobStateService(jobs, outbox,
                Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC));
        job = ReportGenerationJob.builder().id(11L).tenantId(7L)
                .reportType(ReportType.MONTHLY_USAGE_BILLING)
                .periodStart(LocalDate.of(2026, 7, 1)).periodEnd(LocalDate.of(2026, 8, 1))
                .status(ReportStatus.PENDING).attemptCount(0).maxAttempts(3).replayCount(0).build();
        when(jobs.findLockedById(11L)).thenReturn(Optional.of(job));
        when(jobs.findLockedByIdAndTenantId(11L, 7L)).thenReturn(Optional.of(job));
        when(outbox.findByEventKey("report-completed:11")).thenReturn(Optional.empty());
        when(outbox.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCompleteMetadataAndNotificationOutboxTogether() {
        assertTrue(service.claim(11L, "worker", 300).isPresent());
        service.complete(11L, "worker",
                new StoredReport("LOCAL", "7/report.docx", 123L, "abc"),
                "report.docx", "application/docx");

        assertEquals(ReportStatus.COMPLETED, job.getStatus());
        assertEquals("7/report.docx", job.getStorageKey());
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captor.capture());
        assertEquals("report-completed:11", captor.getValue().getEventKey());
        assertEquals(7L, captor.getValue().getTenantId());
    }

    @Test
    void shouldRetryThenDeadLetterAndReplay() {
        service.claim(11L, "worker", 300);
        assertEquals(ReportStatus.RETRY_WAIT,
                service.failed(11L, "worker", new IllegalStateException("secret")));
        assertEquals("IllegalStateException", job.getLastError());

        job.setStatus(ReportStatus.DEAD);
        service.replayDead(7L, 11L);
        assertEquals(ReportStatus.PENDING, job.getStatus());
        assertEquals(Integer.valueOf(1), job.getReplayCount());
    }
}
