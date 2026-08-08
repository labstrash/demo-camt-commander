package com.example.commander.domain.assembly;

import com.example.commander.domain.config.ReportConfigRow;
import org.springframework.stereotype.Component;

/**
 * Factory for selecting the appropriate grouping strategy based on configuration.
 */
@Component
public class MessageGroupingStrategyFactory {
    private final BundledGroupingStrategy bundledStrategy;
    private final UnbundledGroupingStrategy unbundledStrategy;

    public MessageGroupingStrategyFactory(
            BundledGroupingStrategy bundledStrategy, UnbundledGroupingStrategy unbundledStrategy) {
        this.bundledStrategy = bundledStrategy;
        this.unbundledStrategy = unbundledStrategy;
    }

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
