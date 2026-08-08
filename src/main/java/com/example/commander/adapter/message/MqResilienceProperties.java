package com.example.commander.adapter.message;

import com.example.commander.domain.message.ReportType;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for MQ resilience: in-process retry, circuit breaker, and dead-letter recovery.
 *
 * <p>Retry/breaker/backoff defaults are placeholders — size them from real outage/recovery data.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "commander.mq.resilience")
public class MqResilienceProperties {

    @Positive private int retryMaxAttempts = 3;

    @Positive private long retryBackoffMs = 500;

    @Positive private int breakerFailureThreshold = 5;

    @Positive private long breakerCoolDownSeconds = 30;

    @Positive private int deadLetterMaxRetries = 5;

    @Positive private int recoveryBatchSize = 100;

    @Valid private RetryBackoff deadLetterRetryBackoffDefault = new RetryBackoff(60, 3600);

    @Valid private List<RetryBackoffTier> deadLetterRetryBackoffTiers = new ArrayList<>();

    @NotBlank private String recoveryJobCron = "0 */5 * * * ?";

    private Map<ReportType, RetryBackoff> backoffByReportType;

    public List<RetryBackoffTier> getDeadLetterRetryBackoffTiers() {
        return Collections.unmodifiableList(deadLetterRetryBackoffTiers);
    }

    public void setDeadLetterRetryBackoffTiers(List<RetryBackoffTier> deadLetterRetryBackoffTiers) {
        this.deadLetterRetryBackoffTiers =
                deadLetterRetryBackoffTiers != null ? new ArrayList<>(deadLetterRetryBackoffTiers) : new ArrayList<>();
    }

    /** Fails fast if a report type appears in more than one backoff tier. */
    @PostConstruct
    void flattenAndValidateTiers() {
        Map<ReportType, RetryBackoff> flattened = new EnumMap<>(ReportType.class);
        Map<ReportType, Integer> tierIndexByReportType = new EnumMap<>(ReportType.class);

        for (int i = 0; i < deadLetterRetryBackoffTiers.size(); i++) {
            RetryBackoffTier tier = deadLetterRetryBackoffTiers.get(i);
            RetryBackoff backoff = new RetryBackoff(tier.getBaseSeconds(), tier.getMaxSeconds());
            for (ReportType rt : tier.getReportTypes()) {
                Integer existing = tierIndexByReportType.putIfAbsent(rt, i);
                if (existing != null) {
                    throw new IllegalStateException("Report type " + rt
                            + " appears in more than one backoff tier (tier " + existing + " and " + i + ")");
                }
                flattened.put(rt, backoff);
            }
        }
        this.backoffByReportType = flattened;
    }

    /** Returns the effective backoff for {@code reportType} — its tier's, or the default. */
    public RetryBackoff getDeadLetterRetryBackoff(ReportType reportType) {
        if (backoffByReportType == null) flattenAndValidateTiers();
        return backoffByReportType.getOrDefault(reportType, deadLetterRetryBackoffDefault);
    }

    /** Base/ceiling backoff pair for dead-letter recovery retries. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryBackoff {
        @Positive private long baseSeconds;

        @Positive private long maxSeconds;
    }

    /** One dead-letter retry backoff, shared by every report type listed in {@link #reportTypes}. */
    @Getter
    @Setter
    public static class RetryBackoffTier {
        @Positive private long baseSeconds;

        @Positive private long maxSeconds;

        @NotEmpty private List<ReportType> reportTypes = new ArrayList<>();

        public List<ReportType> getReportTypes() {
            return Collections.unmodifiableList(reportTypes);
        }

        public void setReportTypes(List<ReportType> reportTypes) {
            this.reportTypes = reportTypes != null ? new ArrayList<>(reportTypes) : new ArrayList<>();
        }
    }
}
