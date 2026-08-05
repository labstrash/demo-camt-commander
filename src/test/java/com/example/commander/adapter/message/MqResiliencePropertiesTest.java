package com.example.commander.adapter.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.example.commander.adapter.message.MqResilienceProperties.RetryBackoff;
import com.example.commander.adapter.message.MqResilienceProperties.RetryBackoffTier;
import com.example.commander.domain.message.ReportType;
import java.util.List;
import org.junit.jupiter.api.Test;

class MqResiliencePropertiesTest {

    @Test
    void reportTypeListedInATierReturnsThatTiersBackoff() {
        MqResilienceProperties properties = new MqResilienceProperties();
        properties.setDeadLetterRetryBackoffDefault(new RetryBackoff(60, 3600));
        properties.setDeadLetterRetryBackoffTiers(List.of(tier(15, 300, ReportType.CAMT052B, ReportType.CAMT052BT)));

        RetryBackoff backoff = properties.getDeadLetterRetryBackoff(ReportType.CAMT052B);

        assertThat(backoff.getBaseSeconds()).isEqualTo(15);
        assertThat(backoff.getMaxSeconds()).isEqualTo(300);
    }

    @Test
    void reportTypeNotListedInAnyTierFallsBackToTheDefault() {
        MqResilienceProperties properties = new MqResilienceProperties();
        properties.setDeadLetterRetryBackoffDefault(new RetryBackoff(60, 3600));
        properties.setDeadLetterRetryBackoffTiers(List.of(tier(15, 300, ReportType.CAMT052B)));

        RetryBackoff backoff = properties.getDeadLetterRetryBackoff(ReportType.CAMT054C);

        assertThat(backoff.getBaseSeconds()).isEqualTo(60);
        assertThat(backoff.getMaxSeconds()).isEqualTo(3600);
    }

    @Test
    void noTiersConfiguredAlwaysReturnsTheDefault() {
        MqResilienceProperties properties = new MqResilienceProperties();
        properties.setDeadLetterRetryBackoffDefault(new RetryBackoff(60, 3600));

        RetryBackoff backoff = properties.getDeadLetterRetryBackoff(ReportType.CAMT054C);

        assertThat(backoff.getBaseSeconds()).isEqualTo(60);
        assertThat(backoff.getMaxSeconds()).isEqualTo(3600);
    }

    @Test
    void reportTypeListedInMoreThanOneTierFailsFast() {
        MqResilienceProperties properties = new MqResilienceProperties();
        properties.setDeadLetterRetryBackoffTiers(
                List.of(tier(15, 300, ReportType.CAMT052B), tier(120, 7200, ReportType.CAMT052B)));

        assertThatThrownBy(() -> properties.getDeadLetterRetryBackoff(ReportType.CAMT052B))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CAMT052B")
                .hasMessageContaining("more than one");
    }

    private static RetryBackoffTier tier(long baseSeconds, long maxSeconds, ReportType... reportTypes) {
        RetryBackoffTier tier = new RetryBackoffTier();
        tier.setBaseSeconds(baseSeconds);
        tier.setMaxSeconds(maxSeconds);
        tier.setReportTypes(List.of(reportTypes));
        return tier;
    }
}
