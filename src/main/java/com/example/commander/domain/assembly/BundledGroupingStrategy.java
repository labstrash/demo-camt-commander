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
 * Strategy for bundled messages: group all assignments by payment type.
 */
@RequiredArgsConstructor
@Component
public class BundledGroupingStrategy implements MessageGroupingStrategy {
    private final PaymentTypeGrouper grouper;

    @Override
    public List<ScopedAllocation> group(ReportConfigTree tree, Map<AccountKey, AccountBalance> accountBalances) {
        return grouper.groupByPaymentType(tree.scopes(), accountBalances);
    }
}
