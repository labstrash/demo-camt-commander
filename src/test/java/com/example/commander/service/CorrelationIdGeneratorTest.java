package com.example.commander.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commander.domain.message.AccountAllocation;
import com.example.commander.domain.message.AliasAllocation;
import com.example.commander.domain.message.PaymentTypeAllocation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorrelationIdGeneratorTest {

    private final CorrelationIdGenerator generator = new CorrelationIdGenerator();

    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-02T00:00:00Z");

    @Test
    void bundledOrConfigOnlyMessagesAreDeterministicFromConfigIdAndWindowAlone() {
        String first = generator.generate(1L, null, START, END, List.of());
        String second = generator.generate(1L, null, START, END, List.of());

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentConfigIdsProduceDifferentBundledCorrelationIds() {
        String first = generator.generate(1L, null, START, END, List.of());
        String second = generator.generate(2L, null, START, END, List.of());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void unbundledMessagesFoldInTheAccountNumberSoTwoAccountsInTheSameScopeDoNotCollide() {
        PaymentTypeAllocation accountA = new PaymentTypeAllocation(
                "SWISH", List.of(new AccountAllocation("1234", "111", null, "SEK")), List.of());
        PaymentTypeAllocation accountB = new PaymentTypeAllocation(
                "SWISH", List.of(new AccountAllocation("1234", "222", null, "SEK")), List.of());

        String first = generator.generate(1L, 101L, START, END, List.of(accountA));
        String second = generator.generate(1L, 101L, START, END, List.of(accountB));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void unbundledAliasBasedMessageUsesTheAliasValueAsTheAllocationKey() {
        PaymentTypeAllocation aliasGroup =
                new PaymentTypeAllocation("BG", List.of(), List.of(new AliasAllocation("ALIAS-1")));

        String first = generator.generate(1L, 101L, START, END, List.of(aliasGroup));
        String second = generator.generate(1L, 101L, START, END, List.of(aliasGroup));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void unbundledMessageWithEmptyGroupsListThrows() {
        assertThatThrownBy(() -> generator.generate(1L, 101L, START, END, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    void unbundledMessageWithAGroupThatHasNeitherAccountsNorAliasesThrows() {
        PaymentTypeAllocation emptyGroup = new PaymentTypeAllocation("SWISH", List.of(), List.of());

        assertThatThrownBy(() -> generator.generate(1L, 101L, START, END, List.of(emptyGroup)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no accounts or aliases");
    }
}
