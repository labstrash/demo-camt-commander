package com.example.commander.application;

import com.example.commander.adapter.message.MqProperties;
import com.example.commander.adapter.message.ext.ExtProperties;
import com.example.commander.adapter.scheduling.SchedulingProperties;
import com.example.commander.domain.assembly.ReportMessageAssembler;
import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.ext.ExtAccountBalance;
import com.example.commander.domain.ext.ExtBalanceMessage;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
import com.example.commander.domain.message.AssemblyContext;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportContext;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.message.TriggerType;
import com.example.commander.domain.report.ReportWindow;
import com.example.commander.port.AgreementScopeRepository;
import com.example.commander.port.ReportConfigRepository;
import com.example.commander.port.ReportConfigTreeRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a EXT balance push: resolve the recipient from the engagement identifier
 * carried in the message, resolve its {@code ReportConfig} exactly as the on-demand path does,
 * then assemble and deliver through the very same {@link ReportMessageAssembler}/{@link
 * ReportMessageDeliveryService} path every other source uses — the message's parsed account
 * balances ride along on {@link AssemblyContext#accountBalances()} rather than a parallel
 * assembly path, so this trigger source picks up the config's own bundling rule
 * ({@code isBundled}) like every other one.
 *
 * <p>Every resolution miss is logged and the message is dropped, never thrown — a malformed
 * or unrecognized EXT push must not crash its caller ({@code ExtMessageListener}'s listener
 * container). Likewise, an assembled message with nothing matched (no account in the resolved
 * config's tree was covered by this push) is dropped rather than delivered — an empty balance
 * report has nothing to tell the recipient.
 */
@Slf4j
@Service
public class ExtReportOrchestrationService {

    private static final DateTimeFormatter MESSAGE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private final AgreementScopeRepository agreementScopeRepository;
    private final ReportConfigRepository reportConfigRepository;
    private final ReportConfigTreeRepository reportConfigTreeRepository;
    private final ReportMessageAssembler reportMessageAssembler;
    private final ReportMessageDeliveryService deliveryService;
    private final MqProperties mqProperties;
    private final ExtProperties extProperties;
    private final ZoneId businessZone;

    public ExtReportOrchestrationService(
            AgreementScopeRepository agreementScopeRepository,
            ReportConfigRepository reportConfigRepository,
            ReportConfigTreeRepository reportConfigTreeRepository,
            ReportMessageAssembler reportMessageAssembler,
            ReportMessageDeliveryService deliveryService,
            MqProperties mqProperties,
            ExtProperties extProperties,
            SchedulingProperties schedulingProperties) {
        this.agreementScopeRepository = agreementScopeRepository;
        this.reportConfigRepository = reportConfigRepository;
        this.reportConfigTreeRepository = reportConfigTreeRepository;
        this.reportMessageAssembler = reportMessageAssembler;
        this.deliveryService = deliveryService;
        this.mqProperties = mqProperties;
        this.extProperties = extProperties;
        this.businessZone = ZoneId.of(schedulingProperties.getTimezone());
    }

    /**
     * Processes one parsed EXT balance message: resolve → assemble → deliver.
     *
     * @param extMessage the parsed inbound message
     */
    public void process(ExtBalanceMessage extMessage) {
        ReportType reportType = extProperties.getReportType();

        Optional<Long> recipientId =
                agreementScopeRepository.findActiveMessageRecipientId(extMessage.accountOwner(), reportType);
        if (recipientId.isEmpty()) {
            log.warn(
                    "No active agreement scope for EXT accountOwner={}, reportType={} — dropping message",
                    extMessage.accountOwner(),
                    reportType);
            return;
        }

        Optional<RecipientRow> recipientRow = reportConfigRepository.findRecipientById(recipientId.get());
        if (recipientRow.isEmpty()) {
            log.warn(
                    "Agreement chain resolved recipientId={} for EXT accountOwner={} but no matching Recipient"
                            + " row exists — dropping message",
                    recipientId.get(),
                    extMessage.accountOwner());
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

        Instant messageInstant = resolveMessageInstant(extMessage);
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
                indexBalances(extMessage));

        ReportConfigTree tree =
                reportConfigTreeRepository.assembleTrees(List.of(config.get())).getFirst();
        List<ReportMessageEnvelope> envelopes = reportMessageAssembler.assemble(tree, context).stream()
                .filter(envelope -> envelope.payload().hasPaymentTypes())
                .toList();

        if (envelopes.isEmpty()) {
            log.warn(
                    "No account under configId={} matched any balance from EXT accountOwner={} — nothing to send",
                    tree.config().configId(),
                    extMessage.accountOwner());
            return;
        }

        String targetQueue = mqProperties.queueFor(config.get().reportType());
        for (ReportMessageEnvelope envelope : envelopes) {
            log.debug("Report Message={}", envelope.payload());
            deliveryService.deliver(envelope, targetQueue, null, null, null);
        }
    }

    /**
     * Parses {@code extMessage}'s own {@code messageDate}/{@code messageTime} ({@code
     * yyyyMMdd}/{@code HHmmss}) as a local timestamp in the configured business timezone and
     * converts it to UTC — the report window reflects when EXT captured the balances, not
     * when Commander happened to process the push.
     */
    private Instant resolveMessageInstant(ExtBalanceMessage extMessage) {
        LocalDate date = LocalDate.parse(extMessage.messageDate(), MESSAGE_DATE_FORMAT);
        LocalTime time = LocalTime.parse(extMessage.messageTime(), MESSAGE_TIME_FORMAT);
        return ZonedDateTime.of(date, time, businessZone).toInstant();
    }

    private static Map<AccountKey, AccountBalance> indexBalances(ExtBalanceMessage extMessage) {
        Map<AccountKey, AccountBalance> byAccount = new LinkedHashMap<>();
        for (ExtAccountBalance balance : extMessage.accounts()) {
            byAccount.put(
                    new AccountKey(balance.clearingNumber(), balance.accountNumber()),
                    new AccountBalance(balance.balance(), balance.settlementAmount()));
        }
        return byAccount;
    }
}
