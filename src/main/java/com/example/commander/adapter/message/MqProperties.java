package com.example.commander.adapter.message;

import com.example.commander.domain.message.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MQ delivery configuration. When {@code enabled} is true, outgoing report messages are sent
 * to MQ; otherwise, they are only logged. Each {@link ReportType} maps to a target queue name.
 */
@Setter
@Getter
@Validated
@ConfigurationProperties(prefix = "commander.mq")
public class MqProperties {

    private boolean enabled = false;

    @NotEmpty private Map<@NotNull ReportType, @NotBlank String> queues = new EnumMap<>(ReportType.class);

    public Map<ReportType, String> getQueues() {
        return Collections.unmodifiableMap(queues);
    }

    public void setQueues(Map<ReportType, String> queues) {
        this.queues = new EnumMap<>(ReportType.class);
        if (queues != null) {
            this.queues.putAll(queues);
        }
    }

    /**
     * Returns the configured queue name for the given report type.
     *
     * @param reportType the report type
     * @return the target queue name
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
