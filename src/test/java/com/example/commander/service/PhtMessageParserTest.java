package com.example.commander.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commander.domain.pht.PhtAccountBalance;
import com.example.commander.domain.pht.PhtBalanceMessage;
import org.junit.jupiter.api.Test;

class PhtMessageParserTest {

    private final PhtMessageParser parser = new PhtMessageParser();

    // Real sample from docs/pht.txt.
    private static final String SAMPLE =
            "192;01;20260731;173041;062021002635;03;81231;1234564917;4521,94;81231;1234568022;-6768017,24;81231;1234568055;-579660,07";

    @Test
    void parsesTheHeaderFields() {
        PhtBalanceMessage message = parser.parse(SAMPLE);

        assertThat(message.messageLength()).isEqualTo("192");
        assertThat(message.versionNumber()).isEqualTo("01");
        assertThat(message.messageDate()).isEqualTo("20260731");
        assertThat(message.messageTime()).isEqualTo("173041");
        assertThat(message.accountOwner()).isEqualTo("062021002635");
    }

    @Test
    void parsesEveryAccountTriplet() {
        PhtBalanceMessage message = parser.parse(SAMPLE);

        assertThat(message.accounts())
                .containsExactly(
                        new PhtAccountBalance("81231", "1234564917", "4521,94"),
                        new PhtAccountBalance("81231", "1234568022", "-6768017,24"),
                        new PhtAccountBalance("81231", "1234568055", "-579660,07"));
    }

    @Test
    void trimsSpacePaddedFixedWidthFields() {
        String padded =
                "192 ;01;20260731;173041;062021002635 ;03; 81231;1234564917 ; 4521,94;81231;1234568022;-6768017,24;81231;1234568055;-579660,07";

        PhtBalanceMessage message = parser.parse(padded);

        assertThat(message.messageLength()).isEqualTo("192");
        assertThat(message.accountOwner()).isEqualTo("062021002635");
        assertThat(message.accounts().getFirst()).isEqualTo(new PhtAccountBalance("81231", "1234564917", "4521,94"));
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
