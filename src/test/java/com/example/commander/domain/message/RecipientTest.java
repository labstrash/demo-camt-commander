package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RecipientTest {

    @Test
    void constructsWithValidFields() {
        Recipient recipient = new Recipient(1L, RecipientType.ORIGINATOR, "3937231530REP0001", "Team Nirvana A");

        assertThat(recipient.id()).isEqualTo(1L);
        assertThat(recipient.type()).isEqualTo(RecipientType.ORIGINATOR);
        assertThat(recipient.value()).isEqualTo("3937231530REP0001");
        assertThat(recipient.name()).isEqualTo("Team Nirvana A");
    }

    @Test
    void rejectsNonPositiveId() {
        assertThatThrownBy(() -> new Recipient(0L, RecipientType.ORIGINATOR, "addr", "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> new Recipient(1L, null, "addr", "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectsBlankAddress() {
        assertThatThrownBy(() -> new Recipient(1L, RecipientType.BIC, "  ", "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    void rejectsBlankDisplayName() {
        assertThatThrownBy(() -> new Recipient(1L, RecipientType.BIC, "addr", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("display name");
    }
}
