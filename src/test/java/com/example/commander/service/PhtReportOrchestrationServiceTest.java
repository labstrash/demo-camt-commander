package com.example.commander.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commander.adapter.message.MqProperties;
import com.example.commander.adapter.message.pht.PhtProperties;
import com.example.commander.config.SchedulingProperties;
import com.example.commander.domain.audit.ReportCommandAuditStatus;
import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
import com.example.commander.domain.message.AssemblyContext;
import com.example.commander.domain.message.PaymentTypeAllocation;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.message.TriggerType;
import com.example.commander.domain.pht.PhtAccountBalance;
import com.example.commander.domain.pht.PhtBalanceMessage;
import com.example.commander.domain.report.ReportWindow;
import com.example.commander.repository.AgreementScopeRepository;
import com.example.commander.repository.ReportConfigRepository;
import com.example.commander.repository.ReportConfigTreeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhtReportOrchestrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T17:30:41Z");

    /** {@code phtMessage()}'s {@code 20260731}/{@code 173041} as Europe/Stockholm-local (CEST, UTC+2), in UTC. */
    private static final Instant MESSAGE_INSTANT = Instant.parse("2026-07-31T15:30:41Z");

    @Mock
    private AgreementScopeRepository agreementScopeRepository;

    @Mock
    private ReportConfigRepository reportConfigRepository;

    @Mock
    private ReportConfigTreeRepository reportConfigTreeRepository;

    @Mock
    private ReportMessageAssembler reportMessageAssembler;

    @Mock
    private ReportMessageDeliveryService deliveryService;

    private PhtReportOrchestrationService newService() {
        MqProperties mqProperties = new MqProperties();
        mqProperties.setQueues(Map.of(ReportType.CAMT052B, "CAMT.052B.QUEUE"));
        PhtProperties phtProperties = new PhtProperties();
        phtProperties.setReportType(ReportType.CAMT052B);
        SchedulingProperties schedulingProperties = new SchedulingProperties();
        schedulingProperties.setTimezone("Europe/Stockholm");
        return new PhtReportOrchestrationService(
                agreementScopeRepository,
                reportConfigRepository,
                reportConfigTreeRepository,
                reportMessageAssembler,
                deliveryService,
                mqProperties,
                phtProperties,
                schedulingProperties);
    }

    @Test
    void dropsTheMessageWhenNoAgreementScopeResolves() {
        when(agreementScopeRepository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B))
                .thenReturn(Optional.empty());

        newService().process(phtMessage());

        verify(reportConfigRepository, never()).findActiveByRecipientAndReportType(any(Long.class), any());
        verify(reportMessageAssembler, never()).assemble(any(), any());
    }

    @Test
    void dropsTheMessageWhenTheRecipientRowIsMissing() {
        when(agreementScopeRepository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B))
                .thenReturn(Optional.of(999L));
        when(reportConfigRepository.findRecipientById(999L)).thenReturn(Optional.empty());

        newService().process(phtMessage());

        verify(reportConfigRepository, never()).findActiveByRecipientAndReportType(any(Long.class), any());
    }

    @Test
    void dropsTheMessageWhenNoActiveConfigResolves() {
        when(agreementScopeRepository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B))
                .thenReturn(Optional.of(999L));
        when(reportConfigRepository.findRecipientById(999L)).thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, ReportType.CAMT052B))
                .thenReturn(Optional.empty());

        newService().process(phtMessage());

        verify(reportMessageAssembler, never()).assemble(any(), any());
    }

    @Test
    void deliversEveryAssembledEnvelopeThatHasPaymentTypesAndPassesTheAccountBalancesThrough() {
        when(agreementScopeRepository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B))
                .thenReturn(Optional.of(999L));
        when(reportConfigRepository.findRecipientById(999L)).thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, ReportType.CAMT052B))
                .thenReturn(Optional.of(config()));
        ReportConfigTree tree = new ReportConfigTree(config(), List.of());
        when(reportConfigTreeRepository.assembleTrees(List.of(config()))).thenReturn(List.of(tree));
        ReportMessageEnvelope envelope = envelope(true);
        when(reportMessageAssembler.assemble(eq(tree), any())).thenReturn(List.of(envelope));
        when(deliveryService.deliver(envelope, "CAMT.052B.QUEUE", null, null, null))
                .thenReturn(ReportCommandAuditStatus.SENT);

        newService().process(phtMessage());

        verify(deliveryService).deliver(envelope, "CAMT.052B.QUEUE", null, null, null);

        ArgumentCaptor<AssemblyContext> contextCaptor = ArgumentCaptor.forClass(AssemblyContext.class);
        verify(reportMessageAssembler).assemble(eq(tree), contextCaptor.capture());
        AssemblyContext context = contextCaptor.getValue();
        assertThat(context.reportContext().triggerType()).isEqualTo(TriggerType.EXTERNAL);
        assertThat(context.reportContext().window().windowStartUtc()).isEqualTo(MESSAGE_INSTANT);
        assertThat(context.reportContext().window().windowEndUtc()).isEqualTo(MESSAGE_INSTANT);
        assertThat(context.accountBalances())
                .containsEntry(new AccountKey("81231", "1234564917"), new AccountBalance("4521,94", "4521,94"));
    }

    @Test
    void indexesBalanceAndSettlementAmountSeparatelyRatherThanDuplicatingBalanceIntoBoth() {
        // Regression test: indexBalances() used to duplicate the single parsed balance value
        // into both AccountBalance fields. PHT actually sends both as distinct wire fields.
        when(agreementScopeRepository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B))
                .thenReturn(Optional.of(999L));
        when(reportConfigRepository.findRecipientById(999L)).thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, ReportType.CAMT052B))
                .thenReturn(Optional.of(config()));
        ReportConfigTree tree = new ReportConfigTree(config(), List.of());
        when(reportConfigTreeRepository.assembleTrees(List.of(config()))).thenReturn(List.of(tree));
        when(reportMessageAssembler.assemble(eq(tree), any())).thenReturn(List.of(envelope(true)));
        PhtBalanceMessage message = new PhtBalanceMessage(
                "192",
                "01",
                "20260731",
                "173041",
                "062021002635",
                List.of(new PhtAccountBalance("81231", "1234564917", "100,00", "200,00")));

        newService().process(message);

        ArgumentCaptor<AssemblyContext> contextCaptor = ArgumentCaptor.forClass(AssemblyContext.class);
        verify(reportMessageAssembler).assemble(eq(tree), contextCaptor.capture());
        assertThat(contextCaptor.getValue().accountBalances())
                .containsEntry(new AccountKey("81231", "1234564917"), new AccountBalance("100,00", "200,00"));
    }

    @Test
    void derivesTheReportWindowFromTheMessagesOwnDateAndTimeRatherThanProcessingTime() {
        when(agreementScopeRepository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B))
                .thenReturn(Optional.of(999L));
        when(reportConfigRepository.findRecipientById(999L)).thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, ReportType.CAMT052B))
                .thenReturn(Optional.of(config()));
        ReportConfigTree tree = new ReportConfigTree(config(), List.of());
        when(reportConfigTreeRepository.assembleTrees(List.of(config()))).thenReturn(List.of(tree));
        when(reportMessageAssembler.assemble(eq(tree), any())).thenReturn(List.of(envelope(true)));

        newService().process(phtMessage());

        ArgumentCaptor<AssemblyContext> contextCaptor = ArgumentCaptor.forClass(AssemblyContext.class);
        verify(reportMessageAssembler).assemble(eq(tree), contextCaptor.capture());
        assertThat(contextCaptor.getValue().reportContext().window())
                .isEqualTo(new ReportWindow(MESSAGE_INSTANT, MESSAGE_INSTANT));
    }

    @Test
    void deliversEveryEnvelopeWhenAssemblyFansOutToMoreThanOne() {
        when(agreementScopeRepository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B))
                .thenReturn(Optional.of(999L));
        when(reportConfigRepository.findRecipientById(999L)).thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, ReportType.CAMT052B))
                .thenReturn(Optional.of(config()));
        ReportConfigTree tree = new ReportConfigTree(config(), List.of());
        when(reportConfigTreeRepository.assembleTrees(List.of(config()))).thenReturn(List.of(tree));
        ReportMessageEnvelope first = envelope(true, "corr-id-1");
        ReportMessageEnvelope second = envelope(true, "corr-id-2");
        when(reportMessageAssembler.assemble(eq(tree), any())).thenReturn(List.of(first, second));

        newService().process(phtMessage());

        verify(deliveryService).deliver(first, "CAMT.052B.QUEUE", null, null, null);
        verify(deliveryService).deliver(second, "CAMT.052B.QUEUE", null, null, null);
    }

    @Test
    void doesNotDeliverWhenNothingInTheAssembledTreeMatchedAnyBalance() {
        when(agreementScopeRepository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B))
                .thenReturn(Optional.of(999L));
        when(reportConfigRepository.findRecipientById(999L)).thenReturn(Optional.of(recipient()));
        when(reportConfigRepository.findActiveByRecipientAndReportType(999L, ReportType.CAMT052B))
                .thenReturn(Optional.of(config()));
        ReportConfigTree tree = new ReportConfigTree(config(), List.of());
        when(reportConfigTreeRepository.assembleTrees(List.of(config()))).thenReturn(List.of(tree));
        when(reportMessageAssembler.assemble(eq(tree), any())).thenReturn(List.of(envelope(false)));

        newService().process(phtMessage());

        verify(deliveryService, never()).deliver(any(), any(), any(), any(), any());
    }

    private static PhtBalanceMessage phtMessage() {
        return new PhtBalanceMessage(
                "192",
                "01",
                "20260731",
                "173041",
                "062021002635",
                List.of(new PhtAccountBalance("81231", "1234564917", "4521,94", "4521,94")));
    }

    private static RecipientRow recipient() {
        return new RecipientRow(999L, "BIC", "SNDOSESSXXX", "Riksgaldskontoret");
    }

    private static ReportConfigRow config() {
        // reportFrequency is a plain descriptive String here — PHT never parses/validates it,
        // since it derives its own window from the pushed message's own date/time, not from
        // any Quartz schedule. Any valid CAMT.ReportFrequency code works; DAILY is used simply
        // because it isn't one of CAMT052B's actual commander.scheduling.schedules[] entries
        // (EVERY_30_MIN/1_HOUR/2_HOURS/4_HOURS), avoiding the appearance that this PHT-only
        // config is also Quartz-scheduled.
        return new ReportConfigRow(
                1L, 12345678, ReportType.CAMT052B, "1.0", "DAILY", "desc", 999L, "IBAN", true, false, false, true);
    }

    private static ReportMessageEnvelope envelope(boolean withPaymentTypes) {
        return envelope(withPaymentTypes, "corr-id");
    }

    private static ReportMessageEnvelope envelope(boolean withPaymentTypes, String correlationId) {
        List<PaymentTypeAllocation> paymentTypes =
                withPaymentTypes ? List.of(new PaymentTypeAllocation("SWISH", List.of(), List.of())) : List.of();
        ReportMessage payload = new ReportMessage(
                12345678,
                ReportType.CAMT052B,
                "1.0",
                NOW,
                NOW,
                true,
                "IBAN",
                false,
                false,
                TriggerType.EXTERNAL,
                new Recipient(999L, RecipientType.BIC, "SNDOSESSXXX", "Riksgaldskontoret"),
                paymentTypes,
                null,
                correlationId,
                "FIKASE52B123450Q9Z6XZHPAH5R0000");
        return new ReportMessageEnvelope(payload, 1L, null);
    }
}
