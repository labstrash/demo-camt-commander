package com.example.commander.service;

import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
import com.example.commander.domain.message.ScopedAllocation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Strategy for bundled messages: group all assignments by payment type.
 */
@Component
public class BundledGroupingStrategy implements MessageGroupingStrategy {
    private final PaymentTypeGrouper grouper;

    public BundledGroupingStrategy(PaymentTypeGrouper grouper) {
        this.grouper = grouper;
    }

    @Override
    public List<ScopedAllocation> group(ReportConfigTree tree, Map<AccountKey, AccountBalance> accountBalances) {
        return grouper.groupByPaymentType(tree.scopes(), accountBalances);
    }
}
