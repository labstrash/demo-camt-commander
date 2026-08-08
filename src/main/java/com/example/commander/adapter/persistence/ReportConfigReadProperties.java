package com.example.commander.adapter.persistence;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the report generation read layer.
 *
 * <p>Controls pagination, batching, and timeout behavior for fetching report configuration
 * and audit data from the database.
 *
 * <p>Configured via {@code commander.read} prefix in application properties.
 */
@Setter
@Getter
@Validated
@ConfigurationProperties(prefix = "commander.read")
public class ReportConfigReadProperties {

    /** Keyset-pagination page size for ReportConfig queries. */
    @Positive private int pageSize = 500;

    /** Query timeout for staged hierarchy reads. */
    @Positive private int stagedQueryTimeoutSeconds = 10;

    /** Query timeout for TVP-backed staged queries. */
    @Positive private int tvpQueryTimeoutSeconds = 15;
}
