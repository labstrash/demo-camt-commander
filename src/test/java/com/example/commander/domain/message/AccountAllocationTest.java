package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountAllocationTest {

    @Test
    void fourArgConstructorLeavesBalanceAndSettlementAmountNull() {
        AccountAllocation allocation = new AccountAllocation("3300", "1234567", "33001234567", "SEK");

        assertThat(allocation.clearingNumber()).isEqualTo("3300");
        assertThat(allocation.accountNumber()).isEqualTo("1234567");
        assertThat(allocation.accountBban()).isEqualTo("33001234567");
        assertThat(allocation.currency()).isEqualTo("SEK");
        assertThat(allocation.balance()).isNull();
        assertThat(allocation.settlementAmount()).isNull();
    }

    @Test
    void sixArgConstructorCarriesBalanceAndSettlementAmount() {
        AccountAllocation allocation =
                new AccountAllocation("3300", "1234567", "33001234567", "SEK", "-579660,07", "-579660,07");

        assertThat(allocation.balance()).isEqualTo("-579660,07");
        assertThat(allocation.settlementAmount()).isEqualTo("-579660,07");
    }
}
