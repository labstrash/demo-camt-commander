package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RecipientTest {

    @Test
    void constructsWithValidFields() {
        Recipient recipient = new Recipient(RecipientType.SIGNER_ID, "3937231530REP0001", "Team Nirvana A");

        assertThat(recipient.type()).isEqualTo(RecipientType.SIGNER_ID);
        assertThat(recipient.value()).isEqualTo("3937231530REP0001");
        assertThat(recipient.name()).isEqualTo("Team Nirvana A");
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> new Recipient(null, "addr", "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectsBlankAddress() {
        assertThatThrownBy(() -> new Recipient(RecipientType.BIC, "  ", "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    void rejectsBlankDisplayName() {
        assertThatThrownBy(() -> new Recipient(RecipientType.BIC, "addr", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("display name");
    }
}
