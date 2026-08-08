package com.example.commander.adapter.message.ext;

import com.example.commander.domain.message.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the EXT inbound MQ listener.
 *
 * <p>{@code enabled} controls listener registration (via {@code @ConditionalOnProperty}).
 * The {@code reportType} is fixed for this single‑purpose integration and determines
 * which report handling path is used.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "commander.ext")
public class ExtProperties {

    private boolean enabled = false;

    @NotBlank private String queue = "CAMT.EXT.QUEUE";

    @NotBlank private String concurrency = "1-1";

    @NotNull private ReportType reportType = ReportType.CAMT052B;
}
