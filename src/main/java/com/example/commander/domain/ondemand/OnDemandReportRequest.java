package com.example.commander.domain.ondemand;

import com.example.commander.domain.message.ReportType;
import java.time.Instant;
import java.util.Objects;

/**
 * A caller's request to trigger a report outside its schedule, published as a JSON body to
 * {@code CAMT.ONDEMAND.QUEUE}.
 *
 * @param recipientType recipient type (e.g., ORIGINATOR, BIC), resolved the same way the
 *     scheduled path resolves recipients
 * @param recipientValue the recipient's delivery address/endpoint value
 * @param reportType the report type to generate
 * @param reportVersion the report version the caller expects to be current; recorded on the
 *     audit row but not cross-checked against the resolved config's own version — the
 *     upstream caller already guarantees this is correct before publishing
 * @param windowStartUtc start of the reporting window (inclusive), supplied directly by the
 *     caller rather than derived
 * @param windowEndUtc end of the reporting window (exclusive)
 * @param requestorName who initiated this request — required, unlike the scheduled path
 *     where it's always {@code null}
 */
public record OnDemandReportRequest(
        String recipientType,
        String recipientValue,
        ReportType reportType,
        String reportVersion,
        Instant windowStartUtc,
        Instant windowEndUtc,
        String requestorName) {

    public OnDemandReportRequest {
        Objects.requireNonNull(recipientType, "recipientType");
        Objects.requireNonNull(recipientValue, "recipientValue");
        Objects.requireNonNull(reportType, "reportType");
        Objects.requireNonNull(reportVersion, "reportVersion");
        Objects.requireNonNull(windowStartUtc, "windowStartUtc");
        Objects.requireNonNull(windowEndUtc, "windowEndUtc");
        Objects.requireNonNull(requestorName, "requestorName");
    }
}
