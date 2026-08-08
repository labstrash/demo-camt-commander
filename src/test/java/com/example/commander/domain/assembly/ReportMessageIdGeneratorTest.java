package com.example.commander.domain.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commander.domain.message.ReportType;
import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.Test;

class ReportMessageIdGeneratorTest {

    private final ReportMessageIdGenerator generator = new ReportMessageIdGenerator();

    @Test
    void generatesAnIdStartingWithTheDefaultPrefixAndReportTypeDerivedSegment() {
        String messageId = generator.generateMessageId(12345678, ReportType.CAMT054C);

        // ReportType.CAMT054C.code() -> "54C"
        assertThat(messageId).startsWith("FIKASE54C").contains("12345678");
    }

    @Test
    void generatesDifferentIdsOnEachCallForTheSameInputsDueToTheTsidComponent() {
        String first = generator.generateMessageId(1, ReportType.CAMT054C);
        String second = generator.generateMessageId(1, ReportType.CAMT054C);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void usesTheInjectedPrefixInsteadOfTheDefault() {
        ReportMessageIdGenerator custom =
                new ReportMessageIdGenerator("TESTPFX", TSID.Factory.builder().build());

        String messageId = custom.generateMessageId(1, ReportType.CAMT054C);

        assertThat(messageId).startsWith("TESTPFX");
    }

    @Test
    void nullReportTypeThrows() {
        assertThatThrownBy(() -> generator.generateMessageId(1, null)).isInstanceOf(NullPointerException.class);
    }
}
