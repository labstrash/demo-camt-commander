package com.example.commander.domain.message;

import com.example.commander.domain.report.ReportWindow;
import java.util.Objects;

/**
 * Report metadata context for message assembly.
 *
 * <p>Contains the essential report configuration details needed during assembly.
 *
 * @param window the reporting window (start/end validated by {@link ReportWindow} itself)
 * @param reportVersion version of the report to generate
 * @param triggerType how the report was triggered (scheduled or on-demand)
 */
public record ReportContext(ReportWindow window, String reportVersion, TriggerType triggerType) {
    public ReportContext {
        Objects.requireNonNull(window, "window cannot be null");
        Objects.requireNonNull(reportVersion, "reportVersion cannot be null");
        Objects.requireNonNull(triggerType, "triggerType cannot be null");
    }
}
