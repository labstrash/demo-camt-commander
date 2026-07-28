package com.example.commander.repository.tvp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TvpParameterSource}'s null/empty guard clause.
 *
 * <p>The actual TVP binding ({@code SQLServerPreparedStatement#setStructured}) is SQL
 * Server driver-specific and integration-tested separately (see the class Javadoc); the
 * guard clause runs before the {@link PreparedStatement} is touched, so it's unit-testable
 * with a mock that's never expected to be invoked.
 */
@ExtendWith(MockitoExtension.class)
class TvpParameterSourceTest {

    @Mock
    private PreparedStatement preparedStatement;

    @Test
    void bindIdsThrowsForNullIds() {
        assertThatThrownBy(() -> TvpParameterSource.bindIds(preparedStatement, 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or empty");
    }

    @Test
    void bindIdsThrowsForEmptyIds() {
        assertThatThrownBy(() -> TvpParameterSource.bindIds(preparedStatement, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or empty");
    }
}
