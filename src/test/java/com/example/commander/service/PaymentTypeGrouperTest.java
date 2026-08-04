package com.example.commander.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commander.domain.config.AccountAssignmentRow;
import com.example.commander.domain.config.AgreementScopeNode;
import com.example.commander.domain.config.AliasAssignmentRow;
import com.example.commander.domain.config.PaymentTypeAssignmentNode;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
import com.example.commander.domain.message.ScopedAllocation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentTypeGrouperTest {

    private final PaymentTypeGrouper grouper = new PaymentTypeGrouper(new AllocationMapper());

    @Test
    void groupByPaymentTypeMergesSamePaymentTypeAcrossScopesIntoOneGroup() {
        PaymentTypeAssignmentNode scope1Assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L)), List.of());
        PaymentTypeAssignmentNode scope2Assignment =
                new PaymentTypeAssignmentNode(2L, 102L, "SWISH", List.of(account(2L)), List.of());

        List<ScopedAllocation> groups = grouper.groupByPaymentType(
                List.of(
                        new AgreementScopeNode(101L, 1L, "Scope A", List.of(scope1Assignment)),
                        new AgreementScopeNode(102L, 1L, "Scope B", List.of(scope2Assignment))),
                Map.of());

        assertThat(groups).hasSize(1);
        ScopedAllocation group = groups.get(0);
        assertThat(group.scopeId()).isNull();
        assertThat(group.allocation().paymentType()).isEqualTo("SWISH");
        assertThat(group.allocation().accounts()).hasSize(2);
    }

    @Test
    void groupByPaymentTypeKeepsDistinctPaymentTypesSeparate() {
        PaymentTypeAssignmentNode swish =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L)), List.of());
        PaymentTypeAssignmentNode bg = new PaymentTypeAssignmentNode(2L, 101L, "BG", List.of(), List.of(alias(2L)));

        List<ScopedAllocation> groups = grouper.groupByPaymentType(
                List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(swish, bg))), Map.of());

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(g -> g.allocation().paymentType()).containsExactlyInAnyOrder("SWISH", "BG");
    }

    @Test
    void groupByPaymentTypeSkipsDanglingAssignmentEntirelyRatherThanProducingAnEmptyGroup() {
        // Regression test: a dangling assignment (neither accounts nor aliases) must not
        // create an entry for its payment type at all — previously it created an empty
        // PaymentTypeAllocation because computeIfAbsent ran before checking whether the
        // assignment had anything to contribute.
        PaymentTypeAssignmentNode dangling = new PaymentTypeAssignmentNode(1L, 101L, "SWISH", null, null);

        List<ScopedAllocation> groups = grouper.groupByPaymentType(
                List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(dangling))), Map.of());

        assertThat(groups).isEmpty();
    }

    @Test
    void groupByPaymentTypeIgnoresDanglingAssignmentWhenARealAssignmentSharesItsPaymentType() {
        PaymentTypeAssignmentNode real =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L)), List.of());
        PaymentTypeAssignmentNode dangling = new PaymentTypeAssignmentNode(2L, 102L, "SWISH", null, null);

        List<ScopedAllocation> groups = grouper.groupByPaymentType(
                List.of(
                        new AgreementScopeNode(101L, 1L, "Scope A", List.of(real)),
                        new AgreementScopeNode(102L, 1L, "Scope B", List.of(dangling))),
                Map.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).allocation().accounts()).hasSize(1);
    }

    @Test
    void groupByPaymentTypeWithNoAccountBalancesLeavesBalanceFieldsNull() {
        PaymentTypeAssignmentNode assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L)), List.of());

        List<ScopedAllocation> groups = grouper.groupByPaymentType(
                List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(assignment))), Map.of());

        assertThat(groups.get(0).allocation().accounts().get(0).balance()).isNull();
    }

    @Test
    void groupByPaymentTypeWithAccountBalancesAttachesMatchedAndOmitsUnmatched() {
        PaymentTypeAssignmentNode assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L), account(2L)), List.of());
        Map<AccountKey, AccountBalance> balances =
                Map.of(new AccountKey("1234", "56789011"), new AccountBalance("100,00", "100,00"));

        List<ScopedAllocation> groups = grouper.groupByPaymentType(
                List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(assignment))), balances);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).allocation().accounts()).hasSize(1);
        assertThat(groups.get(0).allocation().accounts().get(0).balance()).isEqualTo("100,00");
    }

    @Test
    void groupByPaymentTypeProducesNoGroupWhenAccountBalancesMatchesNothing() {
        PaymentTypeAssignmentNode assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L)), List.of());
        Map<AccountKey, AccountBalance> balances =
                Map.of(new AccountKey("nope", "nope"), new AccountBalance("100,00", "100,00"));

        List<ScopedAllocation> groups = grouper.groupByPaymentType(
                List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(assignment))), balances);

        assertThat(groups).isEmpty();
    }

    @Test
    void groupByRowCreatesOneScopedAllocationPerAccountRow() {
        PaymentTypeAssignmentNode assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L), account(2L)), List.of());

        List<ScopedAllocation> groups =
                grouper.groupByRow(List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(assignment))), Map.of());

        assertThat(groups).hasSize(2);
        assertThat(groups).allMatch(g -> g.scopeId() == 101L);
        assertThat(groups).allMatch(g -> g.allocation().totalAssignments() == 1);
    }

    @Test
    void groupByRowCreatesOneScopedAllocationPerAliasRowPairedWithItsOwnScope() {
        PaymentTypeAssignmentNode scope1Assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "BG", List.of(), List.of(alias(1L)));
        PaymentTypeAssignmentNode scope2Assignment =
                new PaymentTypeAssignmentNode(2L, 102L, "BG", List.of(), List.of(alias(2L)));

        List<ScopedAllocation> groups = grouper.groupByRow(
                List.of(
                        new AgreementScopeNode(101L, 1L, "Scope A", List.of(scope1Assignment)),
                        new AgreementScopeNode(102L, 1L, "Scope B", List.of(scope2Assignment))),
                Map.of());

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(ScopedAllocation::scopeId).containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    void groupByRowProducesNothingForADanglingAssignment() {
        PaymentTypeAssignmentNode dangling = new PaymentTypeAssignmentNode(1L, 101L, "SWISH", null, null);

        List<ScopedAllocation> groups =
                grouper.groupByRow(List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(dangling))), Map.of());

        assertThat(groups).isEmpty();
    }

    @Test
    void groupByRowWithAccountBalancesOmitsRowsWithNoMatch() {
        PaymentTypeAssignmentNode assignment =
                new PaymentTypeAssignmentNode(1L, 101L, "SWISH", List.of(account(1L), account(2L)), List.of());
        Map<AccountKey, AccountBalance> balances =
                Map.of(new AccountKey("1234", "56789011"), new AccountBalance("100,00", "100,00"));

        List<ScopedAllocation> groups =
                grouper.groupByRow(List.of(new AgreementScopeNode(101L, 1L, "Scope A", List.of(assignment))), balances);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).allocation().accounts().get(0).balance()).isEqualTo("100,00");
    }

    private static AccountAssignmentRow account(long id) {
        return new AccountAssignmentRow(id, 1L, "1234", "5678901" + id, null, "SEK");
    }

    private static AliasAssignmentRow alias(long id) {
        return new AliasAssignmentRow(id, 1L, "ALIAS-" + id);
    }
}
