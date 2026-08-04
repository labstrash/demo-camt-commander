package com.example.commander.adapter.message.ondemand;

import com.example.commander.adapter.message.InboundMqListenerConfig;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the inbound on-demand MQ listener.
 *
 * <p>{@link #enabled} gates listener registration entirely (via {@code
 * @ConditionalOnProperty} on {@link InboundMqListenerConfig}/{@link OnDemandMessageListener}) —
 * unlike {@code commander.mq.enabled}, which gates whether an already-registered writer
 * actually sends, this is off by default so a fresh environment doesn't start consuming
 * {@code CAMT.ONDEMAND.QUEUE} until explicitly turned on.
 */
@Validated
@ConfigurationProperties(prefix = "commander.ondemand")
public class OnDemandProperties {

    private boolean enabled = false;

    @NotBlank private String queue = "CAMT.ONDEMAND.QUEUE";

    @NotBlank private String concurrency = "1-1";

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
}
