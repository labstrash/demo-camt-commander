package com.example.commander.domain.assembly;

import com.example.commander.domain.config.ReportConfigRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory for selecting the appropriate grouping strategy based on configuration.
 */
@RequiredArgsConstructor
@Component
public class MessageGroupingStrategyFactory {
    private final BundledGroupingStrategy bundledStrategy;
    private final UnbundledGroupingStrategy unbundledStrategy;

    /**
     * Returns the appropriate grouping strategy for a configuration.
     *
     * @param config the report configuration
     * @return the grouping strategy
     */
    public MessageGroupingStrategy forConfig(ReportConfigRow config) {
        return config.isBundled() ? bundledStrategy : unbundledStrategy;
    }
}
