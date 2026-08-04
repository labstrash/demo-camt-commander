package com.example.commander.service;

import com.example.commander.adapter.message.MqProperties;
import com.example.commander.adapter.message.pht.PhtProperties;
import com.example.commander.config.SchedulingProperties;
import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
import com.example.commander.domain.message.AssemblyContext;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportContext;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.TriggerType;
import com.example.commander.domain.pht.PhtAccountBalance;
import com.example.commander.domain.pht.PhtBalanceMessage;
import com.example.commander.domain.report.ReportWindow;
import com.example.commander.repository.AgreementScopeRepository;
import com.example.commander.repository.ReportConfigRepository;
import com.example.commander.repository.ReportConfigTreeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a PHT balance push: resolve the recipient from the engagement identifier
 * carried in the message, resolve its {@code ReportConfig} exactly as the on-demand path does,
 * then assemble and deliver through the very same {@link ReportMessageAssembler}/{@link
 * ReportMessageDeliveryService} path every other source uses — the message's parsed account
 * balances ride along on {@link AssemblyContext#accountBalances()} rather than a parallel
 * assembly path, so this trigger source picks up the config's own bundling rule
 * ({@code isBundled}) like every other one.
 *
 * <p>Every resolution miss is logged and the message is dropped, never thrown — a malformed
 * or unrecognized PHT push must not crash its caller ({@code PhtMessageListener}'s listener
 * container). Likewise, an assembled message with nothing matched (no account in the resolved
 * config's tree was covered by this push) is dropped rather than delivered — an empty balance
 * report has nothing to tell the recipient.
 */
@Service
public class PhtReportOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(PhtReportOrchestrationService.class);

    private static final DateTimeFormatter MESSAGE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private final AgreementScopeRepository agreementScopeRepository;
    private final ReportConfigRepository reportConfigRepository;
    private final ReportConfigTreeRepository reportConfigTreeRepository;
    private final ReportMessageAssembler reportMessageAssembler;
    private final ReportMessageDeliveryService deliveryService;
    private final MqProperties mqProperties;
    private final PhtProperties phtProperties;
    private final ZoneId businessZone;

    public PhtReportOrchestrationService(
            AgreementScopeRepository agreementScopeRepository,
            ReportConfigRepository reportConfigRepository,
            ReportConfigTreeRepository reportConfigTreeRepository,
            ReportMessageAssembler reportMessageAssembler,
            ReportMessageDeliveryService deliveryService,
            MqProperties mqProperties,
            PhtProperties phtProperties,
            SchedulingProperties schedulingProperties) {
        this.agreementScopeRepository = agreementScopeRepository;
        this.reportConfigRepository = reportConfigRepository;
        this.reportConfigTreeRepository = reportConfigTreeRepository;
        this.reportMessageAssembler = reportMessageAssembler;
        this.deliveryService = deliveryService;
        this.mqProperties = mqProperties;
        this.phtProperties = phtProperties;
        this.businessZone = ZoneId.of(schedulingProperties.getTimezone());
    }

    /**
     * Processes one parsed PHT balance message: resolve → assemble → deliver.
     *
     * @param phtMessage the parsed inbound message
     */
    public void process(PhtBalanceMessage phtMessage) {
        String reportType = phtProperties.getReportType();

        Optional<Long> recipientId =
                agreementScopeRepository.findActiveMessageRecipientId(phtMessage.accountOwner(), reportType);
        if (recipientId.isEmpty()) {
            log.warn(
                    "No active agreement scope for PHT accountOwner={}, reportType={} — dropping message",
                    phtMessage.accountOwner(),
                    reportType);
            return;
        }

        Optional<RecipientRow> recipientRow = reportConfigRepository.findRecipientById(recipientId.get());
        if (recipientRow.isEmpty()) {
            log.warn(
                    "Agreement chain resolved recipientId={} for PHT accountOwner={} but no matching Recipient"
                            + " row exists — dropping message",
                    recipientId.get(),
                    phtMessage.accountOwner());
            return;
        }

        Optional<ReportConfigRow> config =
                reportConfigRepository.findActiveByRecipientAndReportType(recipientId.get(), reportType);
        if (config.isEmpty()) {
            log.warn(
                    "No active ReportConfig for recipientId={}, reportType={} — dropping message",
                    recipientId.get(),
                    reportType);
            return;
        }

        Instant messageInstant = resolveMessageInstant(phtMessage);
        Recipient recipient = new Recipient(
                recipientRow.get().id(),
                RecipientType.valueOf(recipientRow.get().type()),
                recipientRow.get().value(),
                recipientRow.get().name());
        AssemblyContext context = new AssemblyContext(
                new ReportContext(
                        new ReportWindow(messageInstant, messageInstant),
                        config.get().reportVersion(),
                        TriggerType.EXTERNAL),
                recipient,
                null,
                indexBalances(phtMessage));

        ReportConfigTree tree =
                reportConfigTreeRepository.assembleTrees(List.of(config.get())).getFirst();
        List<ReportMessageEnvelope> envelopes = reportMessageAssembler.assemble(tree, context).stream()
                .filter(envelope -> envelope.payload().hasPaymentTypes())
                .toList();

        if (envelopes.isEmpty()) {
            log.warn(
                    "No account under configId={} matched any balance from PHT accountOwner={} — nothing to send",
                    tree.config().configId(),
                    phtMessage.accountOwner());
            return;
        }

        String targetQueue = mqProperties.queueFor(config.get().reportType());
        for (ReportMessageEnvelope envelope : envelopes) {
            log.debug("Report Message={}", envelope.payload());
            deliveryService.deliver(envelope, targetQueue, null, null, null);
        }
    }

    /**
     * Parses {@code phtMessage}'s own {@code messageDate}/{@code messageTime} ({@code
     * yyyyMMdd}/{@code HHmmss}) as a local timestamp in the configured business timezone and
     * converts it to UTC — the report window reflects when PHT captured the balances, not
     * when Commander happened to process the push.
     */
    private Instant resolveMessageInstant(PhtBalanceMessage phtMessage) {
        LocalDate date = LocalDate.parse(phtMessage.messageDate(), MESSAGE_DATE_FORMAT);
        LocalTime time = LocalTime.parse(phtMessage.messageTime(), MESSAGE_TIME_FORMAT);
        return ZonedDateTime.of(date, time, businessZone).toInstant();
    }

    private static Map<AccountKey, AccountBalance> indexBalances(PhtBalanceMessage phtMessage) {
        Map<AccountKey, AccountBalance> byAccount = new LinkedHashMap<>();
        for (PhtAccountBalance balance : phtMessage.accounts()) {
            byAccount.put(
                    new AccountKey(balance.clearingNumber(), balance.accountNumber()),
                    new AccountBalance(balance.balance(), balance.balance()));
        }
        return byAccount;
    }
}
