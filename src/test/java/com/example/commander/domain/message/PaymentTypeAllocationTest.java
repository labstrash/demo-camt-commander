package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentTypeAllocationTest {

    private static final AccountAllocation ACCOUNT = new AccountAllocation("1234", "56789012", null, "SEK");
    private static final AliasAllocation ALIAS = new AliasAllocation("ALIAS-1");

    @Test
    void accountsOnlyAllocationReportsHasAccountsAndNotEmpty() {
        PaymentTypeAllocation allocation = new PaymentTypeAllocation("SWISH", List.of(ACCOUNT), List.of());

        assertThat(allocation.hasAccounts()).isTrue();
        assertThat(allocation.hasAliases()).isFalse();
        assertThat(allocation.hasBothAccountAndAlias()).isFalse();
        assertThat(allocation.isEmpty()).isFalse();
        assertThat(allocation.totalAssignments()).isEqualTo(1);
    }

    @Test
    void aliasesOnlyAllocationReportsHasAliasesAndNotEmpty() {
        PaymentTypeAllocation allocation = new PaymentTypeAllocation("BG", List.of(), List.of(ALIAS));

        assertThat(allocation.hasAccounts()).isFalse();
        assertThat(allocation.hasAliases()).isTrue();
        assertThat(allocation.isEmpty()).isFalse();
        assertThat(allocation.totalAssignments()).isEqualTo(1);
    }

    @Test
    void nullAccountsAndAliasesNormalizeToEmptyListsAndReportEmpty() {
        PaymentTypeAllocation allocation = new PaymentTypeAllocation("SWISH", null, null);

        assertThat(allocation.accounts()).isEmpty();
        assertThat(allocation.aliases()).isEmpty();
        assertThat(allocation.isEmpty()).isTrue();
        assertThat(allocation.hasAccounts()).isFalse();
        assertThat(allocation.hasAliases()).isFalse();
        assertThat(allocation.totalAssignments()).isZero();
    }

    @Test
    void emptyListsForBothAccountsAndAliasesReportEmptyWithoutThrowing() {
        PaymentTypeAllocation allocation = new PaymentTypeAllocation("SWISH", List.of(), List.of());

        assertThat(allocation.isEmpty()).isTrue();
        assertThat(allocation.hasBothAccountAndAlias()).isFalse();
    }

    @Test
    void bothAccountsAndAliasesPopulatedViolatesMutualExclusivityInvariant() {
        // Regression test: the compact constructor previously called hasBothAccountAndAlias(),
        // an instance accessor reading the not-yet-assigned record fields, which NPE'd on
        // every construction rather than ever reaching this check.
        assertThatThrownBy(() -> new PaymentTypeAllocation("SWISH", List.of(ACCOUNT), List.of(ALIAS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SWISH")
                .hasMessageContaining("mutual-exclusivity");
    }
}
