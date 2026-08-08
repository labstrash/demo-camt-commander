package com.example.commander.domain.assembly;

import com.example.commander.domain.config.AccountAssignmentRow;
import com.example.commander.domain.config.AgreementScopeNode;
import com.example.commander.domain.config.AliasAssignmentRow;
import com.example.commander.domain.config.PaymentTypeAssignmentNode;
import com.example.commander.domain.message.AccountAllocation;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
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
     * @param accountBalances externally supplied balances, keyed by account — empty for every
     *     path except the PHT balance use case; when non-empty, an account with no entry is
     *     omitted rather than included with a null balance (see {@link
     *     com.example.commander.domain.message.AssemblyContext#accountBalances()})
     * @return list of scoped payment type groups, one per distinct payment type
     */
    public List<ScopedAllocation> groupByPaymentType(
            List<AgreementScopeNode> scopes, Map<AccountKey, AccountBalance> accountBalances) {
        Map<String, PaymentTypeGroupBuilder> buildersByType = new LinkedHashMap<>();

        for (AgreementScopeNode scope : scopes) {
            for (PaymentTypeAssignmentNode assignment : scope.paymentTypeAssignments()) {
                // A dangling assignment (neither accounts nor aliases) contributes nothing —
                // skip it entirely rather than creating an empty allocation for its payment type.
                if (assignment.isAccountAssignment() || assignment.isAliasAssignment()) {
                    buildersByType
                            .computeIfAbsent(assignment.paymentType(), PaymentTypeGroupBuilder::new)
                            .add(assignment, accountBalances);
                }
            }
        }

        // A builder can end up empty when every one of its account rows was filtered out for
        // having no matching entry in a non-empty accountBalances — never possible before that
        // filtering existed, since every builder here was created for an assignment that itself
        // had accounts or aliases.
        return buildersByType.values().stream()
                .filter(PaymentTypeGroupBuilder::hasContent)
                .map(builder -> new ScopedAllocation(null, builder.build()))
                .toList();
    }

    /**
     * Creates one allocation per individual account or alias row, each paired with its
     * originating scope — for unbundled messages, which fan out to exactly one message
     * per row.
     *
     * @param scopes the agreement scopes to group
     * @param accountBalances externally supplied balances, keyed by account — empty for every
     *     path except the PHT balance use case; when non-empty, an account row with no entry
     *     produces no group at all rather than one carrying a null balance (see {@link
     *     com.example.commander.domain.message.AssemblyContext#accountBalances()})
     * @return list of scoped payment type groups, one per account/alias row
     */
    public List<ScopedAllocation> groupByRow(
            List<AgreementScopeNode> scopes, Map<AccountKey, AccountBalance> accountBalances) {
        List<ScopedAllocation> groups = new ArrayList<>();

        for (AgreementScopeNode scope : scopes) {
            for (PaymentTypeAssignmentNode assignment : scope.paymentTypeAssignments()) {
                for (AccountAssignmentRow account : assignment.accounts()) {
                    List<AccountAllocation> mapped =
                            allocationMapper.toAccountAllocations(List.of(account), accountBalances);
                    if (mapped.isEmpty()) {
                        continue;
                    }
                    PaymentTypeAllocation allocation =
                            new PaymentTypeAllocation(assignment.paymentType(), mapped, List.of());
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

        void add(PaymentTypeAssignmentNode assignment, Map<AccountKey, AccountBalance> accountBalances) {
            if (assignment.isAccountAssignment()) {
                accounts.addAll(allocationMapper.toAccountAllocations(assignment.accounts(), accountBalances));
            } else {
                aliases.addAll(allocationMapper.toAliasAllocations(assignment.aliases()));
            }
        }

        boolean hasContent() {
            return !accounts.isEmpty() || !aliases.isEmpty();
        }

        PaymentTypeAllocation build() {
            return new PaymentTypeAllocation(paymentType, accounts, aliases);
        }
    }
}
