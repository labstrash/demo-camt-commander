package com.example.commander.domain.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.example.commander.domain.config.AccountAssignmentRow;
import com.example.commander.domain.config.AgreementScopeNode;
import com.example.commander.domain.config.AliasAssignmentRow;
import com.example.commander.domain.config.PaymentTypeAssignmentNode;
import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
import com.example.commander.domain.message.AssemblyContext;
import com.example.commander.domain.message.PaymentTypeAllocation;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportContext;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.message.TriggerType;
import com.example.commander.domain.report.ReportWindow;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReportMessageAssemblerTest {

    private final AllocationMapper allocationMapper = new AllocationMapper();
    private final PaymentTypeGrouper grouper = new PaymentTypeGrouper(allocationMapper);
    private final MessageGroupingStrategyFactory strategyFactory = new MessageGroupingStrategyFactory(
            new BundledGroupingStrategy(grouper), new UnbundledGroupingStrategy(grouper));
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final OutboundMessageBuilder messageBuilder = new OutboundMessageBuilder(
            new CorrelationIdGenerator(), new ReportMessageIdGenerator(), new MessageIdValidator(), meterRegistry);
    private final ReportMessageAssembler service = new ReportMessageAssembler(messageBuilder, strategyFactory);

    @Test
    void zeroScopeProducesSingleConfigOnlyMessage() {
        ReportConfigTree tree = new ReportConfigTree(config(true), List.of());

        List<ReportMessageEnvelope> messages = service.assemble(tree, context());

        assertThat(messages).hasSize(1);
        ReportMessageEnvelope message = messages.get(0);
        assertThat(message.payload().hasNoPaymentTypes()).isTrue();
        // bundled is still stamped through even though it drives no fan-out here
        assertThat(message.payload().bundled()).isTrue();
        assertThat(message.scopeId()).isNull();
        assertThat(message.configId()).isEqualTo(1L);
    }

    @Test
    void zeroScopeOnANonBundledConfigStillProducesAValidConfigOnlyMessage() {
        // Regression: OutboundMessageBuilder used to stamp payload.bundled() from the config's
        // own isBundled() flag. A zero-scope config with isBundled=false produced
        // bundled=false + scopeId=null, which violates ReportMessageEnvelope's invariant
        // (unbundled messages must carry a scopeId) and threw IllegalStateException.
        ReportConfigTree tree = new ReportConfigTree(config(false), List.of());

        List<ReportMessageEnvelope> messages = service.assemble(tree, context());

        assertThat(messages).hasSize(1);
        ReportMessageEnvelope message = messages.get(0);
        assertThat(message.payload().hasNoPaymentTypes()).isTrue();
        // scopeId is null (no scope exists), so bundled must be true regardless of the
        // config's own bundling rule, to satisfy the envelope's invariant.
        assertThat(message.payload().bundled()).isTrue();
        assertThat(message.scopeId()).isNull();
    }

    @Test
    void bundledMergesAllAccountsAndAliasesAcrossScopesIntoOneMessage() {
        PaymentTypeAssignmentNode scope1Assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L, 1L)), List.of());
        PaymentTypeAssignmentNode scope2Assignment =
                new PaymentTypeAssignmentNode(2L, 102L, "BG", List.of(), List.of(alias(2L, 2L)));

        ReportConfigTree tree = new ReportConfigTree(
                config(true),
                List.of(
                        new AgreementScopeNode(101L, 1L, "Scope A", List.of(scope1Assignment)),
                        new AgreementScopeNode(102L, 1L, "Scope B", List.of(scope2Assignment))));

        List<ReportMessageEnvelope> messages = service.assemble(tree, context());

        assertThat(messages).hasSize(1);
        ReportMessageEnvelope message = messages.get(0);
        assertThat(message.payload().paymentTypeCount()).isEqualTo(2);
        assertThat(message.payload().totalAccountAssignments()).isEqualTo(1);
        assertThat(message.scopeId()).isNull();

        PaymentTypeAllocation swishAllocation = allocationFor(message, "SWISH");
        assertThat(swishAllocation.accounts()).hasSize(1);
        assertThat(swishAllocation.aliases()).isEmpty();

        PaymentTypeAllocation bgAllocation = allocationFor(message, "BG");
        assertThat(bgAllocation.aliases()).hasSize(1);
        assertThat(bgAllocation.accounts()).isEmpty();
    }

    @Test
    void bundledMergesSamePaymentTypeAcrossScopesIntoOneAllocation() {
        PaymentTypeAssignmentNode scope1Assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L, 1L)), List.of());
        PaymentTypeAssignmentNode scope2Assignment =
                new PaymentTypeAssignmentNode(2L, 102L, "SWISH", List.of(account(2L, 2L)), List.of());

        ReportConfigTree tree = new ReportConfigTree(
                config(true),
                List.of(
                        new AgreementScopeNode(101L, 1L, "Scope A", List.of(scope1Assignment)),
                        new AgreementScopeNode(102L, 1L, "Scope B", List.of(scope2Assignment))));

        List<ReportMessageEnvelope> messages = service.assemble(tree, context());

        assertThat(messages).hasSize(1);
        ReportMessageEnvelope message = messages.get(0);
        // both scopes' SWISH assignments merge into a single allocation, not two
        assertThat(message.payload().paymentTypeCount()).isEqualTo(1);
        assertThat(allocationFor(message, "SWISH").accounts()).hasSize(2);
    }

    @Test
    void unbundledProducesOneMessagePerAccountOrAliasRow() {
        PaymentTypeAssignmentNode scope1Assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L, 1L), account(2L, 1L)), List.of());
        PaymentTypeAssignmentNode scope2Assignment =
                new PaymentTypeAssignmentNode(2L, 102L, "BG", List.of(), List.of(alias(3L, 2L)));

        ReportConfigTree tree = new ReportConfigTree(
                config(false),
                List.of(
                        new AgreementScopeNode(101L, 1L, "Scope A", List.of(scope1Assignment)),
                        new AgreementScopeNode(102L, 1L, "Scope B", List.of(scope2Assignment))));

        List<ReportMessageEnvelope> messages = service.assemble(tree, context());

        // total message count = sum of every account/alias row
        assertThat(messages).hasSize(3);
        assertThat(messages).allMatch(m -> m.payload().paymentTypeCount() == 1);

        // lineage: each message's scopeId matches the scope it actually came from
        assertThat(messages).filteredOn(ReportMessageAssemblerTest::hasAccounts).allMatch(m -> m.scopeId() == 101L);
        assertThat(messages).filteredOn(ReportMessageAssemblerTest::hasAliases).allMatch(m -> m.scopeId() == 102L);

        // each unbundled message's single allocation still carries the originating payment type
        assertThat(messages)
                .filteredOn(ReportMessageAssemblerTest::hasAccounts)
                .allMatch(m -> m.payload().paymentTypes().get(0).paymentType().equals("SWISH"));
        assertThat(messages)
                .filteredOn(ReportMessageAssemblerTest::hasAliases)
                .allMatch(m -> m.payload().paymentTypes().get(0).paymentType().equals("BG"));

        // same-scope collision fix: two unbundled messages from scope 101 (different accounts)
        // must not derive the same correlationId
        Set<String> correlationIdsFromScope101 = messages.stream()
                .filter(ReportMessageAssemblerTest::hasAccounts)
                .map(m -> m.payload().correlationId())
                .collect(Collectors.toSet());
        assertThat(correlationIdsFromScope101).hasSize(2);
    }

    @Test
    void bundledWithAccountBalancesAttachesBalanceToTheMatchedAccount() {
        // Real-collaborator coverage for the EXT balance use case's threading of
        // AssemblyContext.accountBalances() through the whole reused pipeline (strategy ->
        // PaymentTypeGrouper -> AllocationMapper), not just PaymentTypeGrouperTest's
        // grouper-level unit coverage.
        PaymentTypeAssignmentNode assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L, 1L)), List.of());
        ReportConfigTree tree = new ReportConfigTree(
                config(true), List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(assignment))));
        Map<AccountKey, AccountBalance> balances =
                Map.of(new AccountKey("1234", "56789011"), new AccountBalance("4521,94", "4521,94"));

        List<ReportMessageEnvelope> messages = service.assemble(tree, contextWithBalances(balances));

        assertThat(messages).hasSize(1);
        assertThat(allocationFor(messages.get(0), "SWISH").accounts().get(0).balance())
                .isEqualTo("4521,94");
    }

    @Test
    void unbundledWithAccountBalancesFansOutOnlyForMatchedAccounts() {
        PaymentTypeAssignmentNode assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L, 1L), account(2L, 1L)), List.of());
        ReportConfigTree tree = new ReportConfigTree(
                config(false), List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(assignment))));
        // Only account(1L, 1L) — clearingNumber "1234", accountNumber "56789011" — has a balance.
        Map<AccountKey, AccountBalance> balances =
                Map.of(new AccountKey("1234", "56789011"), new AccountBalance("100,00", "100,00"));

        List<ReportMessageEnvelope> messages = service.assemble(tree, contextWithBalances(balances));

        // account(2L, 1L) has no matching balance, so it produces no message at all — not a
        // message with a null balance.
        assertThat(messages).hasSize(1);
        assertThat(allocationFor(messages.get(0), "SWISH").accounts().get(0).balance())
                .isEqualTo("100,00");
    }

    @Test
    void danglingAssignmentWithNeitherAccountsNorAliasesIsSkippedNotCounted() {
        PaymentTypeAssignmentNode danglingAssignment = new PaymentTypeAssignmentNode(1L, 101L, "SWISH", null, null);

        ReportConfigTree tree = new ReportConfigTree(
                config(true), List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(danglingAssignment))));

        List<ReportMessageEnvelope> messages = service.assemble(tree, context());

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).payload().paymentTypeCount()).isZero();
    }

    @Test
    void paymentTypeAssignmentRejectsBothAccountsAndAliases() {
        assertThatThrownBy(() -> new PaymentTypeAssignmentNode(
                        1L, 101L, "SWISH", List.of(account(1L, 1L)), List.of(alias(2L, 1L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mutual-exclusivity");
    }

    @Test
    void correlationIdIsDeterministicAcrossSeparateAssembleCalls() {
        ReportConfigTree tree = new ReportConfigTree(config(true), List.of());

        String first = service.assemble(tree, context()).get(0).payload().correlationId();
        String second = service.assemble(tree, context()).get(0).payload().correlationId();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void messageIdIsNonNullAndWithinLengthBudgetForAnOrdinaryReportType() {
        ReportConfigTree tree = new ReportConfigTree(config(true), List.of());

        String messageId = service.assemble(tree, context()).get(0).payload().id();

        assertThat(messageId).isNotBlank().hasSizeLessThan(35).startsWith("FIKASE");
    }

    @Test
    void messageIdSucceedsAtExactlyTheLengthCeilingForTheLongestConfiguredReportType() {
        // CAMT052BT's report-type-derived segment is 4 characters, one more than every other
        // configured report type (3) — pushes the generated ID to exactly 35 characters.
        // MessageIdValidator's 35-char ceiling is inclusive, so this must succeed, not throw.
        ReportConfigRow longSuffixConfig = new ReportConfigRow(
                1L,
                12345678,
                ReportType.CAMT052BT,
                "1.0",
                "EVERY_30_MIN",
                "desc",
                999L,
                "IBAN",
                true,
                false,
                false,
                true);
        ReportConfigTree tree = new ReportConfigTree(longSuffixConfig, List.of());

        String messageId = service.assemble(tree, context()).get(0).payload().id();

        assertThat(messageId)
                .hasSizeLessThanOrEqualTo(35)
                .startsWith("FIKASE52BT")
                .contains("12345678");
    }

    @Test
    void incrementsTheGeneratedMessagesCounterTaggedByReportTypeTriggerTypeAndBundled() {
        ReportConfigTree tree = new ReportConfigTree(config(true), List.of());

        service.assemble(tree, context());

        double count = meterRegistry
                .get("commander.report.messages.generated")
                .tag("report_type", "CAMT054C")
                .tag("trigger_type", "SCHEDULED")
                .tag("bundled", "true")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }

    private static ReportConfigRow config(boolean bundled) {
        return new ReportConfigRow(
                1L,
                12345678,
                ReportType.CAMT054C,
                "1.0",
                "ONE_TIME_PER_DAY",
                "desc",
                999L,
                "IBAN",
                true,
                false,
                false,
                bundled);
    }

    private static AccountAssignmentRow account(long id, long assignmentId) {
        return new AccountAssignmentRow(id, assignmentId, "1234", "5678901" + id, null, "SEK");
    }

    private static AliasAssignmentRow alias(long id, long assignmentId) {
        return new AliasAssignmentRow(id, assignmentId, "ALIAS-" + id);
    }

    private static PaymentTypeAllocation allocationFor(ReportMessageEnvelope message, String paymentType) {
        return message.payload().paymentTypes().stream()
                .filter(allocation -> allocation.paymentType().equals(paymentType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no allocation for payment type " + paymentType));
    }

    private static boolean hasAccounts(ReportMessageEnvelope message) {
        return message.payload().paymentTypes().stream()
                .anyMatch(allocation -> !allocation.accounts().isEmpty());
    }

    private static boolean hasAliases(ReportMessageEnvelope message) {
        return message.payload().paymentTypes().stream()
                .anyMatch(allocation -> !allocation.aliases().isEmpty());
    }

    private static AssemblyContext context() {
        ReportContext reportContext = new ReportContext(
                new ReportWindow(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z")),
                "1.0",
                TriggerType.SCHEDULED);
        Recipient recipient = new Recipient(RecipientType.BIC, "SOMEBIC", "Some Recipient");
        return new AssemblyContext(reportContext, recipient, null);
    }

    private static AssemblyContext contextWithBalances(Map<AccountKey, AccountBalance> balances) {
        ReportContext reportContext = new ReportContext(
                new ReportWindow(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z")),
                "1.0",
                TriggerType.EXTERNAL);
        Recipient recipient = new Recipient(RecipientType.BIC, "SOMEBIC", "Some Recipient");
        return new AssemblyContext(reportContext, recipient, null, balances);
    }
}
