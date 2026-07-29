package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.Test;

class ReportMessageIdGeneratorTest {

    private final ReportMessageIdGenerator generator = new ReportMessageIdGenerator();

    @Test
    void generatesAnIdStartingWithTheDefaultPrefixAndReportTypeDerivedSegment() {
        String messageId = generator.generateMessageId(12345678, "CAMT054C");

        // "CAMT054C" -> cleaned/upper-cased -> substring from index 5 -> "54C"
        assertThat(messageId).startsWith("FIKASE54C").contains("12345678");
    }

    @Test
    void generatesDifferentIdsOnEachCallForTheSameInputsDueToTheTsidComponent() {
        String first = generator.generateMessageId(1, "CAMT054C");
        String second = generator.generateMessageId(1, "CAMT054C");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void usesTheInjectedPrefixInsteadOfTheDefault() {
        ReportMessageIdGenerator custom =
                new ReportMessageIdGenerator("TESTPFX", TSID.Factory.builder().build());

        String messageId = custom.generateMessageId(1, "CAMT054C");

        assertThat(messageId).startsWith("TESTPFX");
    }

    @Test
    void nullReportTypeThrows() {
        assertThatThrownBy(() -> generator.generateMessageId(1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankReportTypeThrows() {
        assertThatThrownBy(() -> generator.generateMessageId(1, "   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportTypeShorterThanSixCharactersThrows() {
        assertThatThrownBy(() -> generator.generateMessageId(1, "ABCDE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6 characters");
    }

    @Test
    void reportTypeThatOnlyMeetsTheMinimumLengthBeforeHyphenRemovalStillThrows() {
        // Raw length is 7 (>= 6), but hyphen-stripped length is only 4 — the validation must
        // check the cleaned length, not the raw one, or this would slip through and later
        // crash inside substring().
        assertThatThrownBy(() -> generator.generateMessageId(1, "A-B-C-D"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6 characters");
    }
}
