package com.example.commander.adapter.message.ondemand;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the on‑demand inbound MQ listener.
 *
 * <p>{@code enabled} controls listener registration (via {@code @ConditionalOnProperty}).
 * The queue name and concurrency are also configurable.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "commander.ondemand")
public class OnDemandProperties {

    private boolean enabled = false;

    @NotBlank private String queue = "CAMT.ONDEMAND.QUEUE";

    @NotBlank private String concurrency = "1-1";
}
