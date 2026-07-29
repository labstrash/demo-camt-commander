package com.example.commander.service;

import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.ScopedAllocation;
import java.util.List;

/**
 * Strategy for grouping assignments into messages.
 */
public interface MessageGroupingStrategy {
    /**
     * Groups assignments from a configuration tree into payment type groups, each paired
     * with its originating scope (null for bundled groups).
     *
     * @param tree the report configuration tree
     * @return list of scoped payment type groups
     */
    List<ScopedAllocation> group(ReportConfigTree tree);
}
