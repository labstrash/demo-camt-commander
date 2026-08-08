package com.example.commander.adapter.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.message.TriggerType;
import com.example.commander.port.DeadLetterMessageRepository;
import com.example.commander.port.ReportCommandAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DeadLetterRecoveryJobTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Mock
    private DeadLetterMessageRepository repository;

    @Mock
    private ReportCommandAuditRepository auditRepository;

    @Mock
    private ResilientMqSender sender;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MqResilienceProperties properties = properties();
    private final ObjectMapper objectMapper = realObjectMapper();

    private DeadLetterRecoveryJob job;

    @BeforeEach
    void setUp() {
        job = new DeadLetterRecoveryJob(repository, auditRepository, sender, objectMapper, properties, clock);
    }

    @Test
    void doesNothingWhenNoRowsAreDue() {
        when(repository.findDueForRetry(anyInt())).thenReturn(List.of());

        job.execute(null);

        verify(repository, never()).delete(any(Long.class));
        verify(repository, never()).markRetryScheduled(any(Long.class), any(Integer.class), any(), any());
        verify(repository, never()).markFailed(any(Long.class), any(Integer.class), any());
        verify(auditRepository, never()).insert(any());
    }

    @Test
    void findsDueRowsUsingTheConfiguredRecoveryBatchSize() {
        when(repository.findDueForRetry(anyInt())).thenReturn(List.of());

        job.execute(null);

        verify(repository).findDueForRetry(properties.getRecoveryBatchSize());
    }

    @Test
    void successfulResendDeletesTheRowAndWritesASentAuditRow() {
        DeadLetterMessage row = row(1L, 0, 5, ReportType.CAMT054C);
        when(repository.findDueForRetry(anyInt())).thenReturn(List.of(row));
        when(sender.send(row.targetQueue(), row.messagePayload())).thenReturn(SendOutcome.success("jms-msg-id"));

        job.execute(null);

        verify(repository).delete(1L);
        ReportCommandAuditEntry entry = capturedAuditEntry();
        assertThat(entry.status()).isEqualTo(ReportCommandAuditStatus.SENT);
        assertThat(entry.mqMessageId()).isEqualTo("jms-msg-id");
        assertThat(entry.correlationId()).isEqualTo("corr-id");
        assertThat(entry.retryCount()).isEqualTo(1); // row.retryCount()=0, this is attempt 1
        assertThat(entry.jobExecutionId()).isNull();
        assertThat(entry.stepExecutionId()).isNull();
        assertThat(entry.reportFrequency()).isNull();
    }

    @Test
    void failureShortOfMaxRetriesSchedulesTheNextAttemptAndWritesAFailedAuditRow() {
        DeadLetterMessage row = row(1L, 0, 5, ReportType.CAMT054C);
        when(repository.findDueForRetry(anyInt())).thenReturn(List.of(row));
        when(sender.send(row.targetQueue(), row.messagePayload()))
                .thenReturn(SendOutcome.transientExhausted(new RuntimeException("still down")));

        job.execute(null);

        Instant expectedNextRetryAt = NOW.plusSeconds(
                properties.getDeadLetterRetryBackoff(row.reportType()).getBaseSeconds());
        verify(repository).markRetryScheduled(eq(1L), eq(1), eq(expectedNextRetryAt), eq("still down"));
        verify(repository, never()).markFailed(any(Long.class), any(Integer.class), any());
        assertThat(capturedAuditEntry().status()).isEqualTo(ReportCommandAuditStatus.FAILED);
    }

    @Test
    void reportTypesInDifferentTiersGetDifferentBackoff() {
        DeadLetterMessage fastRow = row(1L, 0, 5, ReportType.CAMT052B);
        DeadLetterMessage slowRow = row(2L, 0, 5, ReportType.CAMT053E);
        when(repository.findDueForRetry(anyInt())).thenReturn(List.of(fastRow, slowRow));
        when(sender.send(fastRow.targetQueue(), fastRow.messagePayload()))
                .thenReturn(SendOutcome.transientExhausted(new RuntimeException("still down")));
        when(sender.send(slowRow.targetQueue(), slowRow.messagePayload()))
                .thenReturn(SendOutcome.transientExhausted(new RuntimeException("still down")));

        job.execute(null);

        Instant expectedFastNextRetryAt = NOW.plusSeconds(15);
        Instant expectedSlowNextRetryAt = NOW.plusSeconds(120);
        verify(repository).markRetryScheduled(eq(1L), eq(1), eq(expectedFastNextRetryAt), any());
        verify(repository).markRetryScheduled(eq(2L), eq(1), eq(expectedSlowNextRetryAt), any());
    }

    @Test
    void failureAtMaxRetriesMarksTheRowTerminallyFailed() {
        DeadLetterMessage row = row(1L, 4, 5, ReportType.CAMT054C); // one more failed attempt reaches maxRetries=5
        when(repository.findDueForRetry(anyInt())).thenReturn(List.of(row));
        when(sender.send(row.targetQueue(), row.messagePayload()))
                .thenReturn(SendOutcome.permanent(new RuntimeException("still broken")));

        job.execute(null);

        verify(repository).markFailed(1L, 5, "still broken");
        verify(repository, never()).markRetryScheduled(any(Long.class), any(Integer.class), any(), any());
    }

    @Test
    void unreadablePayloadMarksTheRowFailedWithoutRetryOrAudit() {
        DeadLetterMessage row = new DeadLetterMessage(
                1L,
                "MSG-1",
                1L,
                null,
                ReportType.CAMT054C,
                "not valid json",
                "QUEUE-1",
                0,
                5,
                null,
                "PENDING_RETRY",
                NOW,
                null,
                NOW);
        when(repository.findDueForRetry(anyInt())).thenReturn(List.of(row));

        job.execute(null);

        verify(sender, never()).send(any(), any());
        verify(auditRepository, never()).insert(any());
        verify(repository)
                .markFailed(eq(1L), eq(0), org.mockito.ArgumentMatchers.contains("Unreadable message_payload"));
    }

    private ReportCommandAuditEntry capturedAuditEntry() {
        ArgumentCaptor<ReportCommandAuditEntry> captor = ArgumentCaptor.forClass(ReportCommandAuditEntry.class);
        verify(auditRepository).insert(captor.capture());
        return captor.getValue();
    }

    private static DeadLetterMessage row(long id, int retryCount, int maxRetries, ReportType reportType) {
        return new DeadLetterMessage(
                id,
                "MSG-" + id,
                1L,
                null,
                reportType,
                payloadJson(reportType),
                "QUEUE-" + id,
                retryCount,
                maxRetries,
                null,
                "PENDING_RETRY",
                NOW,
                null,
                NOW);
    }

    /**
     * A real (not mocked) {@code ReportMessage} serialized via a real {@link ObjectMapper} —
     * {@link DeadLetterRecoveryJob} deserializes {@code message_payload} for real, so the test
     * payload needs to actually round-trip, not just be a placeholder string.
     */
    private static String payloadJson(ReportType reportType) {
        ReportMessage payload = new ReportMessage(
                12345678,
                reportType,
                "1.0",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                true,
                "IBAN",
                true,
                true,
                TriggerType.SCHEDULED,
                new Recipient(999L, RecipientType.BIC, "SOMEBIC", "Some Recipient"),
                List.of(),
                null,
                "corr-id",
                "FIKASE054C123450Q9Z6XZHPAH5R0000");
        return realObjectMapper().writeValueAsString(payload);
    }

    /**
     * Jackson 3's {@code jackson-databind} has java.time support built in — no separate {@code
     * JavaTimeModule} registration needed, unlike Jackson 2.
     */
    private static ObjectMapper realObjectMapper() {
        return tools.jackson.databind.json.JsonMapper.builder().build();
    }

    private static MqResilienceProperties properties() {
        MqResilienceProperties properties = new MqResilienceProperties();
        properties.setDeadLetterRetryBackoffDefault(new MqResilienceProperties.RetryBackoff(60, 3600));

        MqResilienceProperties.RetryBackoffTier fastTier = new MqResilienceProperties.RetryBackoffTier();
        fastTier.setBaseSeconds(15);
        fastTier.setMaxSeconds(300);
        fastTier.setReportTypes(List.of(ReportType.CAMT052B));

        MqResilienceProperties.RetryBackoffTier slowTier = new MqResilienceProperties.RetryBackoffTier();
        slowTier.setBaseSeconds(120);
        slowTier.setMaxSeconds(7200);
        slowTier.setReportTypes(List.of(ReportType.CAMT053E));

        properties.setDeadLetterRetryBackoffTiers(List.of(fastTier, slowTier));
        return properties;
    }
}
