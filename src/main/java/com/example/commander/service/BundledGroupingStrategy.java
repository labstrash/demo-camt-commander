package com.example.commander.service;

import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.ScopedAllocation;
import java.util.List;
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
    public List<ScopedAllocation> group(ReportConfigTree tree) {
        return grouper.groupByPaymentType(tree.scopes());
    }
}
