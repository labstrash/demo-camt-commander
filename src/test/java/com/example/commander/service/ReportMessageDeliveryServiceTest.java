package com.example.commander.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commander.adapter.message.MqResilienceProperties;
import com.example.commander.adapter.message.ResilientMqSender;
import com.example.commander.adapter.message.SendOutcome;
import com.example.commander.domain.audit.ReportCommandAuditEntry;
import com.example.commander.domain.audit.ReportCommandAuditStatus;
import com.example.commander.domain.deadletter.DeadLetterMessage;
import com.example.commander.domain.message.AccountAllocation;
import com.example.commander.domain.message.PaymentTypeAllocation;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.message.TriggerType;
import com.example.commander.repository.DeadLetterMessageRepository;
import com.example.commander.repository.ReportCommandAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers dedup-check → send → dead-letter-on-failure → audit-insert, the single send path
 * shared by {@code MqReportMessageWriter} (batch) and, eventually, the on-demand path.
 */
@ExtendWith(MockitoExtension.class)
class ReportMessageDeliveryServiceTest {

    private static final String TARGET_QUEUE = "CAMT.054C.QUEUE";
    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ResilientMqSender sender;

    @Mock
    private DeadLetterMessageRepository deadLetterRepository;

    @Mock
    private ReportCommandAuditRepository auditRepository;

    private ReportMessageDeliveryService newService() {
        MqResilienceProperties resilienceProperties = new MqResilienceProperties();
        resilienceProperties.setDeadLetterMaxRetries(5);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new ReportMessageDeliveryService(
                objectMapper, sender, deadLetterRepository, auditRepository, clock, resilienceProperties);
    }

    @ParameterizedTest
    @MethodSource("failureOutcomes")
    void deadLettersOnAnyFailureOutcomeAndReturnsFailed(SendOutcome outcome) {
        ReportMessageDeliveryService service = newService();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"json\":true}");
        when(sender.send(eq(TARGET_QUEUE), anyString())).thenReturn(outcome);
        ReportMessageEnvelope item = message();

        ReportCommandAuditStatus status = service.deliver(item, TARGET_QUEUE, "DAILY", 111L, 222L);

        assertThat(status).isEqualTo(ReportCommandAuditStatus.FAILED);
        verify(deadLetterRepository)
                .insert(eq(new DeadLetterMessage(
                        item.payload().messageId(),
                        item.configId(),
                        item.scopeId(),
                        ReportType.CAMT054C,
                        "{\"json\":true}",
                        TARGET_QUEUE,
                        5,
                        NOW)));
    }

    @ParameterizedTest
    @MethodSource("failureOutcomes")
    void writesAFailedAuditRowOnAnyFailureOutcome(SendOutcome outcome) {
        ReportMessageDeliveryService service = newService();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"json\":true}");
        when(sender.send(eq(TARGET_QUEUE), anyString())).thenReturn(outcome);
        ReportMessageEnvelope item = message();

        service.deliver(item, TARGET_QUEUE, "DAILY", 111L, 222L);

        ReportCommandAuditEntry entry = capturedAuditEntry();
        assertThat(entry.status()).isEqualTo(ReportCommandAuditStatus.FAILED);
        assertThat(entry.mqMessageId()).isNull();
        assertThat(entry.retryCount()).isZero();
        assertThat(entry.jobExecutionId()).isEqualTo(111L);
        assertThat(entry.stepExecutionId()).isEqualTo(222L);
        assertThat(entry.reportFrequency()).isEqualTo("DAILY");
    }

    @Test
    void successfulSendNeverWritesADeadLetterRow() {
        ReportMessageDeliveryService service = newService();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"json\":true}");
        when(sender.send(eq(TARGET_QUEUE), anyString())).thenReturn(SendOutcome.success("jms-msg-id"));

        service.deliver(message(), TARGET_QUEUE, "DAILY", 111L, 222L);

        verify(deadLetterRepository, never()).insert(any());
    }

    @Test
    void successfulSendWritesASentAuditRowWithTheRealJmsMessageIdAndReturnsSent() {
        ReportMessageDeliveryService service = newService();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"json\":true}");
        when(sender.send(eq(TARGET_QUEUE), anyString())).thenReturn(SendOutcome.success("jms-msg-id"));

        ReportCommandAuditStatus status = service.deliver(message(), TARGET_QUEUE, "DAILY", 111L, 222L);

        assertThat(status).isEqualTo(ReportCommandAuditStatus.SENT);
        ReportCommandAuditEntry entry = capturedAuditEntry();
        assertThat(entry.status()).isEqualTo(ReportCommandAuditStatus.SENT);
        assertThat(entry.mqMessageId()).isEqualTo("jms-msg-id");
    }

    @Test
    void auditEntryFieldsAreMappedFromTheEnvelopeAndItsPayloadCorrectly() {
        // Locks in the envelope/payload -> audit-entry mapping: reportConfigId comes from the
        // envelope's own surrogate configId, while configId (the audit column) comes from the
        // payload's own business configId — the two are distinct fields that happen to share
        // a confusingly similar name. recipientType/recipientValue map to Recipient.type()/
        // .value() (not a nonexistent .value()), and accountCount maps to
        // ReportMessage.totalAccountAssignments() (not a nonexistent .accountCount()).
        ReportMessageDeliveryService service = newService();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"json\":true}");
        when(sender.send(eq(TARGET_QUEUE), anyString())).thenReturn(SendOutcome.success("jms-msg-id"));

        service.deliver(message(), TARGET_QUEUE, "DAILY", 111L, 222L);

        ReportCommandAuditEntry entry = capturedAuditEntry();
        assertThat(entry.reportConfigId()).isEqualTo(1L); // envelope.configId()
        assertThat(entry.configId()).isEqualTo("12345678"); // payload.configId()
        assertThat(entry.agreementScopeId()).isNull(); // envelope.scopeId()
        assertThat(entry.isBundled()).isTrue();
        assertThat(entry.accountCount()).isEqualTo(2);
        assertThat(entry.paymentTypeCount()).isEqualTo(1);
        assertThat(entry.recipientType()).isEqualTo("BIC");
        assertThat(entry.recipientValue()).isEqualTo("SOMEBIC");
    }

    @Test
    void serializationFailureIsRecordedAsFailedWithoutDeadLetteringOrCrashing() {
        ReportMessageDeliveryService service = newService();
        JacksonException serializationFailure = new JacksonException("boom") {};
        when(objectMapper.writeValueAsString(any())).thenThrow(serializationFailure);

        ReportCommandAuditStatus status = service.deliver(message(), TARGET_QUEUE, "DAILY", 111L, 222L);

        assertThat(status).isEqualTo(ReportCommandAuditStatus.FAILED);
        verify(sender, never()).send(any(), any());
        // No valid, resendable payload exists to dead-letter — retrying an unserializable
        // object would fail identically every time.
        verify(deadLetterRepository, never()).insert(any());
        ReportCommandAuditEntry entry = capturedAuditEntry();
        assertThat(entry.status()).isEqualTo(ReportCommandAuditStatus.FAILED);
        assertThat(entry.errorMessage()).contains("Serialization failed").contains("boom");
    }

    @Test
    void existingSentRowForCorrelationIdSkipsTheSendEntirelyAndReturnsSkippedDuplicate() {
        ReportMessageDeliveryService service = newService();
        when(auditRepository.existsSent("corr-id")).thenReturn(true);

        ReportCommandAuditStatus status = service.deliver(message(), TARGET_QUEUE, "DAILY", 111L, 222L);

        assertThat(status).isEqualTo(ReportCommandAuditStatus.SKIPPED_DUPLICATE);
        verify(sender, never()).send(any(), any());
        verify(deadLetterRepository, never()).insert(any());
        ReportCommandAuditEntry entry = capturedAuditEntry();
        assertThat(entry.status()).isEqualTo(ReportCommandAuditStatus.SKIPPED_DUPLICATE);
    }

    @Test
    void nullBatchContextIsPassedThroughUnchangedForTheOnDemandPath() {
        ReportMessageDeliveryService service = newService();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"json\":true}");
        when(sender.send(eq(TARGET_QUEUE), anyString())).thenReturn(SendOutcome.success("jms-msg-id"));

        service.deliver(message(), TARGET_QUEUE, null, null, null);

        ReportCommandAuditEntry entry = capturedAuditEntry();
        assertThat(entry.reportFrequency()).isNull();
        assertThat(entry.jobExecutionId()).isNull();
        assertThat(entry.stepExecutionId()).isNull();
    }

    private ReportCommandAuditEntry capturedAuditEntry() {
        ArgumentCaptor<ReportCommandAuditEntry> captor = ArgumentCaptor.forClass(ReportCommandAuditEntry.class);
        verify(auditRepository).insert(captor.capture());
        return captor.getValue();
    }

    private static Stream<SendOutcome> failureOutcomes() {
        return Stream.of(
                SendOutcome.breakerOpen(),
                SendOutcome.transientExhausted(new RuntimeException("boom")),
                SendOutcome.permanent(new RuntimeException("boom")));
    }

    private static ReportMessageEnvelope message() {
        PaymentTypeAllocation allocation = new PaymentTypeAllocation(
                "SWISH",
                List.of(
                        new AccountAllocation("3300", "1234567", "33001234567", "SEK"),
                        new AccountAllocation("3300", "7654321", "33007654321", "SEK")),
                List.of());
        ReportMessage payload = new ReportMessage(
                12345678,
                ReportType.CAMT054C,
                "1.0",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                true,
                "IBAN",
                true,
                true,
                TriggerType.SCHEDULED,
                new Recipient(999L, RecipientType.BIC, "SOMEBIC", "Some Recipient"),
                List.of(allocation),
                null,
                "corr-id",
                "FIKASE054C123450Q9Z6XZHPAH5R0000");
        return new ReportMessageEnvelope(payload, 1L, null);
    }
}
