package com.example.commander.adapter.message.pht;

import com.example.commander.adapter.message.InboundMqListenerConfig;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the inbound PHT balance MQ listener.
 *
 * <p>{@link #enabled} gates listener registration entirely (via {@code
 * @ConditionalOnProperty} on {@link InboundMqListenerConfig}/{@link PhtMessageListener}), same
 * posture as {@link com.example.commander.adapter.message.ondemand.OnDemandProperties}.
 *
 * <p>{@link #reportType} is the fixed report type this integration always targets — PHT is a
 * single-purpose integration for one recipient/report config, not a dynamic per-message
 * choice (see the phase's Open Questions on {@code EngagementBank}).
 */
@Validated
@ConfigurationProperties(prefix = "commander.pht")
public class PhtProperties {

    private boolean enabled = false;

    @NotBlank private String queue = "CAMT.PHT.QUEUE";

    @NotBlank private String concurrency = "1-1";

    @NotBlank private String reportType = "CAMT052B";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(String concurrency) {
        this.concurrency = concurrency;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }
}
