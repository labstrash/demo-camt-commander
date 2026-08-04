package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commander.domain.report.ReportWindow;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssemblyContextTest {

    private static final ReportContext REPORT_CONTEXT = new ReportContext(
            new ReportWindow(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z")),
            "1.0",
            TriggerType.SCHEDULED);
    private static final Recipient RECIPIENT = new Recipient(999L, RecipientType.BIC, "SOMEBIC", "Some Recipient");

    @Test
    void constructsWithNullRequestorNameForScheduledRuns() {
        AssemblyContext context = new AssemblyContext(REPORT_CONTEXT, RECIPIENT, null);

        assertThat(context.reportContext()).isEqualTo(REPORT_CONTEXT);
        assertThat(context.recipient()).isEqualTo(RECIPIENT);
        assertThat(context.requestorName()).isNull();
    }

    @Test
    void constructsWithARequestorNameForOnDemandRuns() {
        AssemblyContext context = new AssemblyContext(REPORT_CONTEXT, RECIPIENT, "alice");

        assertThat(context.requestorName()).isEqualTo("alice");
    }

    @Test
    void rejectsNullReportContext() {
        assertThatThrownBy(() -> new AssemblyContext(null, RECIPIENT, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullRecipient() {
        assertThatThrownBy(() -> new AssemblyContext(REPORT_CONTEXT, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void threeArgConstructorDefaultsAccountBalancesToEmpty() {
        AssemblyContext context = new AssemblyContext(REPORT_CONTEXT, RECIPIENT, null);

        assertThat(context.accountBalances()).isEmpty();
    }

    @Test
    void fourArgConstructorStoresAccountBalances() {
        Map<AccountKey, AccountBalance> balances =
                Map.of(new AccountKey("81231", "1234564917"), new AccountBalance("4521,94", "4521,94"));

        AssemblyContext context = new AssemblyContext(REPORT_CONTEXT, RECIPIENT, null, balances);

        assertThat(context.accountBalances()).isEqualTo(balances);
    }

    @Test
    void nullAccountBalancesNormalizeToAnEmptyMapRatherThanNull() {
        AssemblyContext context = new AssemblyContext(REPORT_CONTEXT, RECIPIENT, null, null);

        assertThat(context.accountBalances()).isNotNull().isEmpty();
    }
}
