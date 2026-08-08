package com.example.commander.domain.assembly;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commander.domain.message.ReportType;
import org.junit.jupiter.api.Test;

class MessageIdValidatorTest {

    private final MessageIdValidator validator = new MessageIdValidator();

    @Test
    void acceptsAMessageIdUnderTheLengthCeiling() {
        String id34Chars = "F".repeat(34);

        assertThatCode(() -> validator.validate(id34Chars, 1, ReportType.CAMT054C))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAMessageIdExactlyAtTheLengthCeiling() {
        // The ceiling (35) is inclusive - exactly 35 is valid, not rejected.
        String id35Chars = "F".repeat(35);

        assertThatCode(() -> validator.validate(id35Chars, 1, ReportType.CAMT054C))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAMessageIdOneOverTheLengthCeiling() {
        String id36Chars = "F".repeat(36);

        assertThatThrownBy(() -> validator.validate(id36Chars, 1, ReportType.CAMT054C))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("36")
                .hasMessageContaining("CAMT054C")
                .hasMessageContaining("configId=1");
    }

    @Test
    void rejectsANullMessageId() {
        assertThatThrownBy(() -> validator.validate(null, 1, ReportType.CAMT054C))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be null");
    }
}
