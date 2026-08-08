package com.example.commander.domain.assembly;

import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
import com.example.commander.domain.message.ScopedAllocation;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Strategy for unbundled messages: create one group per individual account or alias row.
 */
@RequiredArgsConstructor
@Component
public class UnbundledGroupingStrategy implements MessageGroupingStrategy {
    private final PaymentTypeGrouper grouper;

    @Override
    public List<ScopedAllocation> group(ReportConfigTree tree, Map<AccountKey, AccountBalance> accountBalances) {
        return grouper.groupByRow(tree.scopes(), accountBalances);
    }
}
