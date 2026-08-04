package com.example.commander.service;

import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
import com.example.commander.domain.message.ScopedAllocation;
import java.util.List;
import java.util.Map;

/**
 * Strategy for grouping assignments into messages.
 */
public interface MessageGroupingStrategy {
    /**
     * Groups assignments from a configuration tree into payment type groups, each paired
     * with its originating scope (null for bundled groups).
     *
     * @param tree the report configuration tree
     * @param accountBalances externally supplied balances, keyed by account — empty for every
     *     path except the PHT balance use case (see {@link
     *     com.example.commander.domain.message.AssemblyContext#accountBalances()})
     * @return list of scoped payment type groups
     */
    List<ScopedAllocation> group(ReportConfigTree tree, Map<AccountKey, AccountBalance> accountBalances);
}
