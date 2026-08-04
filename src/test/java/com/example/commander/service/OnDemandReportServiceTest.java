package com.example.commander.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commander.adapter.message.MqProperties;
import com.example.commander.domain.audit.ReportCommandAuditEntry;
import com.example.commander.domain.audit.ReportCommandAuditStatus;
import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.TriggerType;
import com.example.commander.domain.ondemand.OnDemandReportRequest;
import com.example.commander.domain.ondemand.OnDemandReportResult;
import com.example.commander.repository.ReportCommandAuditRepository;
import com.example.commander.repository.ReportConfigRepository;
import com.example.commander.repository.ReportConfigTreeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnDemandReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-07-02T00:00:00Z");

    @Mock
    private ReportConfigRepository reportConfigRepository;

    @Mock
    private ReportConfigTreeRepository reportConfigTreeRepository;

    @Mock
    private ReportMessageAssembler reportMessageAssembler;

    @Mock
    private ReportMessageDeliveryService deliveryService;

    @Mock
    private ReportCommandAuditRepository auditRepository;

    private OnDemandReportService newService() {
        MqProperties mqProperties = new MqProperties();
        mqProperties.setQueues(java.util.Map.of("CAMT054C", "CAMT.054C.QUEUE"));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new OnDemandReportService(
                reportConfigRepository,
                reportConfigTreeRepository,
                reportMessageAssembler,
                deliveryService,
                auditRepository,
                mqProperties,
                new CorrelationIdGenerator(),
                new com.example.commander.domain.message.ReportMessageIdGenerator(),
                clock);
    }

    @Test
    void rejectsWhenRecipientNotFound() {
        when(reportConfigRepository.findRecipientByTypeAndValue("BIC", "SOMEBIC"))
                .thenReturn(Optional.empty());

        OnDemandReportResult result = newService().trigger(request());

        assertThat(result.status()).isEqualTo(ReportCommandAuditStatus.REJECTED_CONFIG_NOT_ELIGIBLE);
        assertThat(result.messages()).isEmpty();
        assertThat(result.detail()).contains("No recipient found");
        ReportCommandAuditEntry entry = capturedAuditEntry();
        assertThat(entry.status()).isEqualTo(ReportCommandAuditStatus.REJECTED_CONFIG_NOT_ELIGIBLE);
        assertThat(entry.reportConfigId()).isNull();
        assertThat(entry.reportVersion()).isEqualTo("N/A");
    }

    @Test
    void rejectsWhenConfigNotFound() {
        when(reportConfigRepository.findRecipientByTypeAndValue("BIC", "SOMEBIC"))
                .thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, "CAMT054C"))
                .thenReturn(Optional.empty());

        OnDemandReportResult result = newService().trigger(request());

        assertThat(result.status()).isEqualTo(ReportCommandAuditStatus.REJECTED_CONFIG_NOT_ELIGIBLE);
        verify(reportMessageAssembler, never()).assemble(any(), any());
    }

    @Test
    void rejectsWhenWindowEndsAfterNow() {
        when(reportConfigRepository.findRecipientByTypeAndValue("BIC", "SOMEBIC"))
                .thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, "CAMT054C"))
                .thenReturn(Optional.of(config()));

        OnDemandReportRequest futureWindowRequest = new OnDemandReportRequest(
                "BIC", "SOMEBIC", "CAMT054C", "1.0", NOW.minusSeconds(60), NOW.plusSeconds(3600), "alice");

        OnDemandReportResult result = newService().trigger(futureWindowRequest);

        assertThat(result.status()).isEqualTo(ReportCommandAuditStatus.REJECTED_INVALID_WINDOW);
        assertThat(result.detail()).contains("ends after now");
        verify(reportMessageAssembler, never()).assemble(any(), any());
    }

    @Test
    void rejectsWhenWindowStartIsAfterEnd() {
        when(reportConfigRepository.findRecipientByTypeAndValue("BIC", "SOMEBIC"))
                .thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, "CAMT054C"))
                .thenReturn(Optional.of(config()));

        OnDemandReportRequest invalidRequest =
                new OnDemandReportRequest("BIC", "SOMEBIC", "CAMT054C", "1.0", WINDOW_END, WINDOW_START, "alice");

        OnDemandReportResult result = newService().trigger(invalidRequest);

        assertThat(result.status()).isEqualTo(ReportCommandAuditStatus.REJECTED_INVALID_WINDOW);
        verify(reportMessageAssembler, never()).assemble(any(), any());
    }

    @Test
    void successfulSingleMessageReturnsSent() {
        when(reportConfigRepository.findRecipientByTypeAndValue("BIC", "SOMEBIC"))
                .thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, "CAMT054C"))
                .thenReturn(Optional.of(config()));
        ReportConfigTree tree = new ReportConfigTree(config(), List.of());
        when(reportConfigTreeRepository.assembleTrees(List.of(config()))).thenReturn(List.of(tree));
        ReportMessageEnvelope envelope = envelope();
        when(reportMessageAssembler.assemble(eq(tree), any())).thenReturn(List.of(envelope));
        when(deliveryService.deliver(envelope, "CAMT.054C.QUEUE", null, null, null))
                .thenReturn(ReportCommandAuditStatus.SENT);

        OnDemandReportResult result = newService().trigger(request());

        assertThat(result.status()).isEqualTo(ReportCommandAuditStatus.SENT);
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).messageId())
                .isEqualTo(envelope.payload().messageId());
        verify(auditRepository, never()).insert(any());
    }

    @Test
    void everyMessageAlreadySentMakesTheOverallStatusSkippedDuplicate() {
        when(reportConfigRepository.findRecipientByTypeAndValue("BIC", "SOMEBIC"))
                .thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, "CAMT054C"))
                .thenReturn(Optional.of(config()));
        ReportConfigTree tree = new ReportConfigTree(config(), List.of());
        when(reportConfigTreeRepository.assembleTrees(List.of(config()))).thenReturn(List.of(tree));
        ReportMessageEnvelope envelope = envelope();
        when(reportMessageAssembler.assemble(eq(tree), any())).thenReturn(List.of(envelope));
        when(deliveryService.deliver(envelope, "CAMT.054C.QUEUE", null, null, null))
                .thenReturn(ReportCommandAuditStatus.SKIPPED_DUPLICATE);

        OnDemandReportResult result = newService().trigger(request());

        assertThat(result.status()).isEqualTo(ReportCommandAuditStatus.SKIPPED_DUPLICATE);
        assertThat(result.messages()).hasSize(1);
    }

    @Test
    void anyFailedMessageMakesTheOverallStatusFailed() {
        when(reportConfigRepository.findRecipientByTypeAndValue("BIC", "SOMEBIC"))
                .thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, "CAMT054C"))
                .thenReturn(Optional.of(config()));
        ReportConfigTree tree = new ReportConfigTree(config(), List.of());
        when(reportConfigTreeRepository.assembleTrees(List.of(config()))).thenReturn(List.of(tree));
        ReportMessageEnvelope sentEnvelope = envelope();
        ReportMessageEnvelope failedEnvelope = envelope();
        when(reportMessageAssembler.assemble(eq(tree), any())).thenReturn(List.of(sentEnvelope, failedEnvelope));
        when(deliveryService.deliver(sentEnvelope, "CAMT.054C.QUEUE", null, null, null))
                .thenReturn(ReportCommandAuditStatus.SENT);
        when(deliveryService.deliver(failedEnvelope, "CAMT.054C.QUEUE", null, null, null))
                .thenReturn(ReportCommandAuditStatus.FAILED);

        OnDemandReportResult result = newService().trigger(request());

        assertThat(result.status()).isEqualTo(ReportCommandAuditStatus.FAILED);
        assertThat(result.messages()).hasSize(2);
    }

    private ReportCommandAuditEntry capturedAuditEntry() {
        ArgumentCaptor<ReportCommandAuditEntry> captor = ArgumentCaptor.forClass(ReportCommandAuditEntry.class);
        verify(auditRepository).insert(captor.capture());
        return captor.getValue();
    }

    private static OnDemandReportRequest request() {
        return new OnDemandReportRequest("BIC", "SOMEBIC", "CAMT054C", "1.0", WINDOW_START, WINDOW_END, "alice");
    }

    private static RecipientRow recipient() {
        return new RecipientRow(999L, "BIC", "SOMEBIC", "Some Recipient");
    }

    private static ReportConfigRow config() {
        return new ReportConfigRow(
                1L, 12345678, "CAMT054C", "1.0", "DAILY", "desc", 999L, "IBAN", true, false, false, true);
    }

    private static ReportMessageEnvelope envelope() {
        ReportMessage payload = new ReportMessage(
                12345678,
                "CAMT054C",
                "1.0",
                WINDOW_START,
                WINDOW_END,
                true,
                TriggerType.ON_DEMAND,
                new Recipient(999L, RecipientType.BIC, "SOMEBIC", "Some Recipient"),
                List.of(),
                "alice",
                "corr-id-" + java.util.UUID.randomUUID(),
                "FIKASE054C123450Q9Z6XZHPAH5R0000");
        return new ReportMessageEnvelope(payload, 1L, null);
    }
}
