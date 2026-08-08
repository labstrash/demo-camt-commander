package com.example.commander.domain.assembly;

import com.example.commander.domain.message.ReportType;
import io.hypersistence.tsid.TSID;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Generates unique identifiers for report messages.
 *
 * <p>Format: PREFIX + typeCode + reportId + timestamp + pageNumber
 * <p>Example: FIKASE752BT0Q9Z6XZHPAH5R0000
 *
 * <p>The fixed-width budget (PREFIX 6 + reportId 8 + timestamp/TSID 13 + pageNumber 4 = 31)
 * leaves 3 characters of headroom for typeCode before reaching {@link
 * com.example.commander.domain.assembly.MessageIdValidator}'s 35-character ceiling — {@code
 * CAMT052BT}, the longest currently configured report type, needs 4 ("52BT") and lands
 * exactly at that ceiling with an 8-digit reportId. The ceiling is inclusive (exactly 35 is
 * valid), which is what keeps this case within budget; a report type longer than {@code
 * CAMT052BT}, or a 9+-digit reportId, would need the ceiling revisited again.
 *
 * <p>Thread-safe and reusable.
 */
@Component
public class ReportMessageIdGenerator {
    private static final String DEFAULT_PREFIX = "FIKASE";
    private static final String DEFAULT_PAGE_SUFFIX = "0000";

    private final String prefix;
    private final TSID.Factory tsidFactory;

    public ReportMessageIdGenerator() {
        this(DEFAULT_PREFIX, TSID.Factory.builder().build());
    }

    public ReportMessageIdGenerator(
            @Value("${report.message.id.prefix:FIKASE}") String prefix, TSID.Factory tsidFactory) {
        this.prefix = prefix;
        this.tsidFactory = tsidFactory;
    }

    /**
     * Generates a unique identifier for a report message.
     *
     * @param reportId The ID of the report (unique per report)
     * @param reportType The type of the report
     * @return A unique identifier string for the report message
     * @throws NullPointerException if reportType is null
     */
    public String generateMessageId(long reportId, ReportType reportType) {
        Objects.requireNonNull(reportType, "reportType cannot be null");

        String timestamp = generateTimestamp();
        String pageNumber = getPageSuffix();

        return String.format("%s%s%d%s%s", prefix, reportType.code(), reportId, timestamp, pageNumber);
    }

    private String generateTimestamp() {
        return tsidFactory.generate().toString();
    }

    private String getPageSuffix() {
        // Future enhancement: calculate from pagination
        return DEFAULT_PAGE_SUFFIX;
    }
}
