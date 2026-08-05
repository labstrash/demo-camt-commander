package com.example.commander.adapter.message;

import com.example.commander.domain.message.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MQ delivery configuration properties.
 *
 * <p>Configured via {@code commander.mq} prefix in application properties.
 *
 * <ul>
 *   <li>{@code commander.mq.enabled} — feature flag gating real MQ delivery. When {@code
 *       false} (the default), {@code CompositeReportMessageWriter} logs only, same as
 *       before this flag existed. When {@code true}, it logs and also sends to MQ.
 *   <li>{@code commander.mq.queues.<reportType>} — target MQ queue name for that report
 *       type.
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "commander.mq")
public class MqProperties {

    /** Feature flag gating real MQ delivery — see the class Javadoc. */
    private boolean enabled = false;

    @NotEmpty private Map<@NotNull ReportType, @NotBlank String> queues = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<ReportType, String> getQueues() {
        return Collections.unmodifiableMap(queues);
    }

    public void setQueues(Map<ReportType, String> queues) {
        this.queues = queues != null ? new HashMap<>(queues) : new HashMap<>();
    }

    /**
     * Returns the target queue name for the given report type.
     *
     * @param reportType the report type
     * @return the configured queue name
     * @throws IllegalArgumentException if no queue is configured for this report type
     */
    public String queueFor(ReportType reportType) {
        String queue = queues.get(reportType);
        if (queue == null) {
            throw new IllegalArgumentException("No commander.mq.queues entry configured for reportType=" + reportType);
        }
        return queue;
    }
}
