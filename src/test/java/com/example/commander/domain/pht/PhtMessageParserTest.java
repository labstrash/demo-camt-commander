package com.example.commander.domain.pht;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhtMessageParserTest {

    private final PhtMessageParser parser = new PhtMessageParser();

    // Real sample, fixed-width space-padded balance/settlementAmount fields (16 chars each,
    // right-justified) as PHT actually sends it — a quadruplet per account, not a triplet:
    // (clearingNumber;accountNumber;balance;settlementAmount). An earlier version of this
    // parser assumed a triplet based on an incomplete extract in docs/pht.txt, whose sample
    // only ever showed balance == settlementAmount for every account and so never surfaced the
    // missing fourth field.
    private static final String SAMPLE = "192;01;20260731;173041;012345678901;03;"
            + "11011;1234567917;         4521,94;         4521,94;"
            + "11011;1234567022;     -6768017,24;     -6768017,24;"
            + "11011;1234567055;      -579660,07;      -579660,07";

    @Test
    void parsesTheHeaderFields() {
        PhtBalanceMessage message = parser.parse(SAMPLE);

        assertThat(message.messageLength()).isEqualTo("192");
        assertThat(message.versionNumber()).isEqualTo("01");
        assertThat(message.messageDate()).isEqualTo("20260731");
        assertThat(message.messageTime()).isEqualTo("173041");
        assertThat(message.accountOwner()).isEqualTo("012345678901");
    }

    @Test
    void parsesEveryAccountQuadrupletTrimmingTheFixedWidthPadding() {
        PhtBalanceMessage message = parser.parse(SAMPLE);

        assertThat(message.accounts())
                .containsExactly(
                        new PhtAccountBalance("11011", "1234567917", "4521,94", "4521,94"),
                        new PhtAccountBalance("11011", "1234567022", "-6768017,24", "-6768017,24"),
                        new PhtAccountBalance("11011", "1234567055", "-579660,07", "-579660,07"));
    }

    @Test
    void parsesDistinctBalanceAndSettlementAmountWithoutSwappingThem() {
        // Guards against a bug where balance/settlementAmount get swapped or aliased to the
        // same value — SAMPLE alone can't catch this since both happen to be equal there.
        String distinct = "192;01;20260731;173041;012345678901;01;11011;1234567917;100,00;200,00";

        PhtBalanceMessage message = parser.parse(distinct);

        assertThat(message.accounts())
                .containsExactly(new PhtAccountBalance("11011", "1234567917", "100,00", "200,00"));
    }

    @Test
    void trimsSpacePaddedFixedWidthFields() {
        String padded = "192 ;01;20260731;173041;012345678901 ;03; 11011;1234567917 ; 4521,94; 4521,94;"
                + "11011;1234567022;-6768017,24;-6768017,24;11011;1234567055;-579660,07;-579660,07";

        PhtBalanceMessage message = parser.parse(padded);

        assertThat(message.messageLength()).isEqualTo("192");
        assertThat(message.accountOwner()).isEqualTo("012345678901");
        assertThat(message.accounts().getFirst())
                .isEqualTo(new PhtAccountBalance("11011", "1234567917", "4521,94", "4521,94"));
    }

    @Test
    void rejectsNullOrBlankInput() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooFewHeaderFields() {
        assertThatThrownBy(() -> parser.parse("192;01;20260731;173041"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header");
    }

    @Test
    void rejectsANonNumericAccountCount() {
        assertThatThrownBy(() -> parser.parse("192;01;20260731;173041;062021002635;NOTANUMBER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountCount");
    }

    @Test
    void rejectsAFieldCountThatDoesNotMatchAccountCount() {
        // Declares 3 accounts but only supplies 2.
        String truncated =
                "192;01;20260731;173041;062021002635;03;81231;1234564917;4521,94;81231;1234568022;-6768017,24";

        assertThatThrownBy(() -> parser.parse(truncated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountCount=3");
    }

    @Test
    void zeroAccountsIsValid() {
        PhtBalanceMessage message = parser.parse("192;01;20260731;173041;062021002635;00");

        assertThat(message.accounts()).isEmpty();
    }
}
