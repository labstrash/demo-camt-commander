package com.example.commander.service;

import com.example.commander.domain.config.AccountAssignmentRow;
import com.example.commander.domain.config.AgreementScopeNode;
import com.example.commander.domain.config.AliasAssignmentRow;
import com.example.commander.domain.config.PaymentTypeAssignmentNode;
import com.example.commander.domain.message.AccountAllocation;
import com.example.commander.domain.message.AliasAllocation;
import com.example.commander.domain.message.PaymentTypeAllocation;
import com.example.commander.domain.message.ScopedAllocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Groups assignments by payment type for message assembly.
 */
@Component
public class PaymentTypeGrouper {
    private final AllocationMapper allocationMapper;

    public PaymentTypeGrouper(AllocationMapper allocationMapper) {
        this.allocationMapper = allocationMapper;
    }

    /**
     * Merges all assignments across scopes into one allocation per distinct payment type,
     * for bundled messages. {@code scopeId} is always null — a bundled group has no single
     * owning scope.
     *
     * @param scopes the agreement scopes to group
     * @return list of scoped payment type groups, one per distinct payment type
     */
    public List<ScopedAllocation> groupByPaymentType(List<AgreementScopeNode> scopes) {
        Map<String, PaymentTypeGroupBuilder> buildersByType = new LinkedHashMap<>();

        for (AgreementScopeNode scope : scopes) {
            for (PaymentTypeAssignmentNode assignment : scope.paymentTypeAssignments()) {
                // A dangling assignment (neither accounts nor aliases) contributes nothing —
                // skip it entirely rather than creating an empty allocation for its payment type.
                if (assignment.isAccountAssignment() || assignment.isAliasAssignment()) {
                    buildersByType
                            .computeIfAbsent(assignment.paymentType(), PaymentTypeGroupBuilder::new)
                            .add(assignment);
                }
            }
        }

        return buildersByType.values().stream()
                .map(builder -> new ScopedAllocation(null, builder.build()))
                .toList();
    }

    /**
     * Creates one allocation per individual account or alias row, each paired with its
     * originating scope — for unbundled messages, which fan out to exactly one message
     * per row.
     *
     * @param scopes the agreement scopes to group
     * @return list of scoped payment type groups, one per account/alias row
     */
    public List<ScopedAllocation> groupByRow(List<AgreementScopeNode> scopes) {
        List<ScopedAllocation> groups = new ArrayList<>();

        for (AgreementScopeNode scope : scopes) {
            for (PaymentTypeAssignmentNode assignment : scope.paymentTypeAssignments()) {
                for (AccountAssignmentRow account : assignment.accounts()) {
                    PaymentTypeAllocation allocation = new PaymentTypeAllocation(
                            assignment.paymentType(),
                            allocationMapper.toAccountAllocations(List.of(account)),
                            List.of());
                    groups.add(new ScopedAllocation(scope.id(), allocation));
                }
                for (AliasAssignmentRow alias : assignment.aliases()) {
                    PaymentTypeAllocation allocation = new PaymentTypeAllocation(
                            assignment.paymentType(), List.of(), allocationMapper.toAliasAllocations(List.of(alias)));
                    groups.add(new ScopedAllocation(scope.id(), allocation));
                }
            }
        }

        return groups;
    }

    private class PaymentTypeGroupBuilder {
        private final String paymentType;
        private final List<AccountAllocation> accounts = new ArrayList<>();
        private final List<AliasAllocation> aliases = new ArrayList<>();

        PaymentTypeGroupBuilder(String paymentType) {
            this.paymentType = paymentType;
        }

        void add(PaymentTypeAssignmentNode assignment) {
            if (assignment.isAccountAssignment()) {
                accounts.addAll(allocationMapper.toAccountAllocations(assignment.accounts()));
            } else {
                aliases.addAll(allocationMapper.toAliasAllocations(assignment.aliases()));
            }
        }

        PaymentTypeAllocation build() {
            return new PaymentTypeAllocation(paymentType, accounts, aliases);
        }
    }
}
