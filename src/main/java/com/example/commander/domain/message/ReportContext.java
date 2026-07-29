package com.example.commander.domain.message;

import java.time.Instant;
import java.util.Objects;

/**
 * Report metadata context for message assembly.
 *
 * <p>Contains the essential report configuration details needed during assembly.
 *
 * @param windowStartUtc start of the reporting window
 * @param windowEndUtc end of the reporting window
 * @param reportVersion version of the report to generate
 * @param triggerType how the report was triggered (scheduled or on-demand)
 */
public record ReportContext(
        Instant windowStartUtc, Instant windowEndUtc, String reportVersion, TriggerType triggerType) {
    public ReportContext {
        Objects.requireNonNull(windowStartUtc, "windowStartUtc cannot be null");
        Objects.requireNonNull(windowEndUtc, "windowEndUtc cannot be null");
        Objects.requireNonNull(reportVersion, "reportVersion cannot be null");
        Objects.requireNonNull(triggerType, "triggerType cannot be null");

        if (windowStartUtc.isAfter(windowEndUtc)) {
            throw new IllegalArgumentException(String.format(
                    "windowStartUtc (%s) must be before windowEndUtc (%s)", windowStartUtc, windowEndUtc));
        }
    }
}
