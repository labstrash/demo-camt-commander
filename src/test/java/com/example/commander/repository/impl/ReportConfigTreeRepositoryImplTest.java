package com.example.commander.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.commander.config.ReportConfigReadProperties;
import com.example.commander.domain.config.AccountAssignmentRow;
import com.example.commander.domain.config.AgreementScopeRow;
import com.example.commander.domain.config.AliasAssignmentRow;
import com.example.commander.domain.config.PaymentTypeAssignmentRow;
import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.ReportType;
import java.util.List;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

/**
 * Unit tests for the guard-clause / short-circuit behavior of
 * {@link ReportConfigTreeRepositoryImpl} — the parts of this class that don't require a
 * live database.
 *
 * <p>The TVP-backed staged queries ({@code findAssignmentsByScopeIds},
 * {@code findAccountsByAssignmentIds}, {@code findAliasesByAssignmentIds}) rely on SQL
 * Server-specific JDBC APIs ({@code SQLServerPreparedStatement#setStructured}) that can't
 * be meaningfully faked, so their actual query execution is integration-tested against
 * real SQL Server (Testcontainers) rather than unit-tested here. What's still valuable —
 * and fully unit-testable — is the empty-input short-circuiting (no query issued at all
 * for an empty ID collection) and the level 1→2 safe-size guard that protects against
 * exceeding SQL Server's 2,100-parameter cap.
 *
 * <p>{@code findConfigPage} and the non-empty path of {@code findScopesByConfigIds} run
 * through an internally-scoped {@code NamedParameterJdbcTemplate} built off the injected
 * {@link DataSource} (see the constructor) rather than the injected {@link JdbcTemplate}
 * mock — there's no seam to intercept, so their real query execution is likewise an
 * integration-test concern, not a unit-test one. {@link #assembleTrees}'s own
 * orchestration logic (staged scopes → assignments → accounts/aliases, ID chaining,
 * error propagation) is still fully unit-testable by spying on the repository and
 * stubbing its own public stage methods, bypassing JDBC entirely — see {@code
 * assembleTreesWiresScopesAssignmentsAndAccountsThroughTheStagedPipeline} below.
 */
@ExtendWith(MockitoExtension.class)
class ReportConfigTreeRepositoryImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    private ReportConfigTreeRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        // The repository builds its own internally-scoped JdbcTemplate off the same
        // DataSource to apply a distinct query timeout without mutating the shared bean
        // (see the class-level constructor comment) — a non-null DataSource is required
        // for construction to succeed, but is never actually connected to in these tests.
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        repository = new ReportConfigTreeRepositoryImpl(jdbcTemplate, new ReportConfigReadProperties());
        // Construction itself calls jdbcTemplate.getDataSource() — reset so the
        // verifyNoInteractions() assertions below only cover behavior under test.
        clearInvocations(jdbcTemplate);
    }

    @Test
    void findScopesByConfigIdsReturnsEmptyListForEmptyInputWithoutQuerying() {
        List<AgreementScopeRow> result = repository.findScopesByConfigIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void findScopesByConfigIdsThrowsWhenExceedingSafeInListSize() {
        List<Long> tooManyIds = LongStream.range(0, 2001).boxed().toList();

        assertThatThrownBy(() -> repository.findScopesByConfigIds(tooManyIds))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2001")
                .hasMessageContaining("2000");
    }

    @Test
    void findAssignmentsByScopeIdsReturnsEmptyListForEmptyInputWithoutQuerying() {
        List<PaymentTypeAssignmentRow> result = repository.findAssignmentsByScopeIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void findAccountsByAssignmentIdsReturnsEmptyListForEmptyInputWithoutQuerying() {
        List<AccountAssignmentRow> result = repository.findAccountsByAssignmentIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void findAliasesByAssignmentIdsReturnsEmptyListForEmptyInputWithoutQuerying() {
        List<AliasAssignmentRow> result = repository.findAliasesByAssignmentIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAssignmentsByScopeIdsBindsIdsAsTvpAndReturnsMappedRows() {
        PaymentTypeAssignmentRow assignment = new PaymentTypeAssignmentRow(201L, 101L, "SWISH");
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenReturn(List.of(assignment));

        List<PaymentTypeAssignmentRow> result = repository.findAssignmentsByScopeIds(List.of(101L));

        assertThat(result).containsExactly(assignment);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAssignmentsByScopeIdsPropagatesFailureWithoutSwallowingIt() {
        // No partial persistence, no bespoke rollback — a staged-query failure
        // propagates to the caller as-is rather than being swallowed or wrapped.
        RuntimeException dbFailure = new RuntimeException("simulated TVP query timeout");
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenThrow(dbFailure);

        assertThatThrownBy(() -> repository.findAssignmentsByScopeIds(List.of(101L)))
                .isSameAs(dbFailure);
    }

    @Test
    void assembleTreesReturnsEmptyListForEmptyConfigsWithoutQuerying() {
        List<ReportConfigRow> result = repository.assembleTrees(List.of()).stream()
                .map(t -> t.config())
                .toList();

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void assembleTreesWiresScopesAssignmentsAndAccountsThroughTheStagedPipeline() {
        ReportConfigTreeRepositoryImpl spyRepository = spy(repository);
        ReportConfigRow config = config(1L);
        AgreementScopeRow scope = new AgreementScopeRow(101L, 1L, "Scope A");
        PaymentTypeAssignmentRow assignment = new PaymentTypeAssignmentRow(201L, 101L, "SWISH");
        AccountAssignmentRow account = new AccountAssignmentRow(301L, 201L, "1234", "5678901", null, "SEK");
        doReturn(List.of(scope)).when(spyRepository).findScopesByConfigIds(List.of(1L));
        doReturn(List.of(assignment)).when(spyRepository).findAssignmentsByScopeIds(List.of(101L));
        doReturn(List.of(account)).when(spyRepository).findAccountsByAssignmentIds(List.of(201L));
        doReturn(List.of()).when(spyRepository).findAliasesByAssignmentIds(List.of(201L));

        List<ReportConfigTree> trees = spyRepository.assembleTrees(List.of(config));

        assertThat(trees).hasSize(1);
        ReportConfigTree tree = trees.getFirst();
        assertThat(tree.scopes()).hasSize(1);
        assertThat(tree.scopes().getFirst().paymentTypeAssignments()).hasSize(1);
        assertThat(tree.scopes().getFirst().paymentTypeAssignments().getFirst().accounts())
                .containsExactly(account);
        verify(spyRepository).findScopesByConfigIds(List.of(1L));
        verify(spyRepository).findAssignmentsByScopeIds(List.of(101L));
        verify(spyRepository).findAccountsByAssignmentIds(List.of(201L));
        verify(spyRepository).findAliasesByAssignmentIds(List.of(201L));
    }

    @Test
    void assembleTreesPropagatesAStagedQueryFailureWithoutSwallowingIt() {
        ReportConfigTreeRepositoryImpl spyRepository = spy(repository);
        ReportConfigRow config = config(1L);
        RuntimeException dbFailure = new RuntimeException("simulated staged query failure");
        doThrow(dbFailure).when(spyRepository).findScopesByConfigIds(List.of(1L));

        assertThatThrownBy(() -> spyRepository.assembleTrees(List.of(config))).isSameAs(dbFailure);
    }

    private static ReportConfigRow config(long id) {
        return new ReportConfigRow(
                id,
                (int) (10000000 + id),
                ReportType.CAMT054C,
                "1.0",
                "DAILY",
                "desc",
                999L,
                "IBAN",
                true,
                false,
                false,
                true);
    }
}
