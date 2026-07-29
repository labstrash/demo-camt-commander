package com.example.commander.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.example.commander.domain.config.AccountAssignmentRow;
import com.example.commander.domain.config.AgreementScopeNode;
import com.example.commander.domain.config.AliasAssignmentRow;
import com.example.commander.domain.config.PaymentTypeAssignmentNode;
import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AssemblyContext;
import com.example.commander.domain.message.PaymentTypeAllocation;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportContext;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportMessageIdGenerator;
import com.example.commander.domain.message.TriggerType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReportMessageAssemblerTest {

    private final AllocationMapper allocationMapper = new AllocationMapper();
    private final PaymentTypeGrouper grouper = new PaymentTypeGrouper(allocationMapper);
    private final MessageGroupingStrategyFactory strategyFactory = new MessageGroupingStrategyFactory(
            new BundledGroupingStrategy(grouper), new UnbundledGroupingStrategy(grouper));
    private final OutboundMessageBuilder messageBuilder = new OutboundMessageBuilder(
            new CorrelationIdGenerator(), new ReportMessageIdGenerator(), new MessageIdValidator());
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
        assertThat(messages).filteredOn(ReportMessageAssemblerTest::hasAccounts).allMatch(m -> m.payload()
                .paymentTypeAllocations()
                .get(0)
                .paymentType()
                .equals("SWISH"));
        assertThat(messages).filteredOn(ReportMessageAssemblerTest::hasAliases).allMatch(m -> m.payload()
                .paymentTypeAllocations()
                .get(0)
                .paymentType()
                .equals("BG"));

        // same-scope collision fix: two unbundled messages from scope 101 (different accounts)
        // must not derive the same correlationId
        Set<String> correlationIdsFromScope101 = messages.stream()
                .filter(ReportMessageAssemblerTest::hasAccounts)
                .map(m -> m.payload().correlationId())
                .collect(Collectors.toSet());
        assertThat(correlationIdsFromScope101).hasSize(2);
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

        String messageId = service.assemble(tree, context()).get(0).payload().messageId();

        assertThat(messageId).isNotBlank().hasSizeLessThan(35).startsWith("FIKASE");
    }

    @Test
    void messageIdSucceedsAtExactlyTheLengthCeilingForTheLongestConfiguredReportType() {
        // CAMT052BT's report-type-derived segment is 4 characters, one more than every other
        // configured report type (3) — pushes the generated ID to exactly 35 characters.
        // MessageIdValidator's 35-char ceiling is inclusive, so this must succeed, not throw.
        ReportConfigRow longSuffixConfig = new ReportConfigRow(
                1L, 12345678, "CAMT052BT", "1.0", "EVERY_30_MIN", "desc", 999L, "IBAN", true, false, false, true);
        ReportConfigTree tree = new ReportConfigTree(longSuffixConfig, List.of());

        String messageId = service.assemble(tree, context()).get(0).payload().messageId();

        assertThat(messageId)
                .hasSizeLessThanOrEqualTo(35)
                .startsWith("FIKASE52BT")
                .contains("12345678");
    }

    private static ReportConfigRow config(boolean bundled) {
        return new ReportConfigRow(
                1L, 12345678, "CAMT054C", "1.0", "ONE_TIME_PER_DAY", "desc", 999L, "IBAN", true, false, false, bundled);
    }

    private static AccountAssignmentRow account(long id, long assignmentId) {
        return new AccountAssignmentRow(id, assignmentId, "1234", "5678901" + id, null, "SEK");
    }

    private static AliasAssignmentRow alias(long id, long assignmentId) {
        return new AliasAssignmentRow(id, assignmentId, "ALIAS-" + id);
    }

    private static PaymentTypeAllocation allocationFor(ReportMessageEnvelope message, String paymentType) {
        return message.payload().paymentTypeAllocations().stream()
                .filter(allocation -> allocation.paymentType().equals(paymentType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no allocation for payment type " + paymentType));
    }

    private static boolean hasAccounts(ReportMessageEnvelope message) {
        return message.payload().paymentTypeAllocations().stream()
                .anyMatch(allocation -> !allocation.accounts().isEmpty());
    }

    private static boolean hasAliases(ReportMessageEnvelope message) {
        return message.payload().paymentTypeAllocations().stream()
                .anyMatch(allocation -> !allocation.aliases().isEmpty());
    }

    private static AssemblyContext context() {
        ReportContext reportContext = new ReportContext(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                "1.0",
                TriggerType.SCHEDULED);
        Recipient recipient = new Recipient(999L, RecipientType.BIC, "SOMEBIC", "Some Recipient");
        return new AssemblyContext(reportContext, recipient, null);
    }
}
