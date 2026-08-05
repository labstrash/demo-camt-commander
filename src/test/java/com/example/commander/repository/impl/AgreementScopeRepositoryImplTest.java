package com.example.commander.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.commander.domain.message.ReportType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Unit tests for {@link AgreementScopeRepositoryImpl}, mocking {@link JdbcTemplate} the same
 * way {@link ReportConfigRepositoryImplTest} does — no real database needed.
 */
@ExtendWith(MockitoExtension.class)
class AgreementScopeRepositoryImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AgreementScopeRepositoryImpl repository;

    @Test
    void returnsEmptyWhenNoMatch() {
        repository = new AgreementScopeRepositoryImpl(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("CAMT052B"), eq("062021002635")))
                .thenReturn(List.of());

        Optional<Long> result = repository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsTheRecipientIdOnASingleMatch() {
        repository = new AgreementScopeRepositoryImpl(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("CAMT052B"), eq("062021002635")))
                .thenReturn(List.of(999L));

        Optional<Long> result = repository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B);

        assertThat(result).contains(999L);
    }

    @Test
    void throwsWhenMultipleRowsMatch() {
        repository = new AgreementScopeRepositoryImpl(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("CAMT052B"), eq("062021002635")))
                .thenReturn(List.of(999L, 998L));

        assertThatThrownBy(() -> repository.findActiveMessageRecipientId("062021002635", ReportType.CAMT052B))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2");
    }
}
