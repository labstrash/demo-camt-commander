package com.example.commander.domain.assembly;

import com.example.commander.domain.config.AccountAssignmentRow;
import com.example.commander.domain.config.AliasAssignmentRow;
import com.example.commander.domain.message.AccountAllocation;
import com.example.commander.domain.message.AccountBalance;
import com.example.commander.domain.message.AccountKey;
import com.example.commander.domain.message.AliasAllocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps domain assignment rows to message allocation objects.
 */
@Component
public class AllocationMapper {

    /**
     * Converts account assignment rows to account allocations, optionally attaching an
     * externally supplied balance.
     *
     * <p>When {@code accountBalances} is empty (every path except the PHT balance use case),
     * every account is included with {@code balance}/{@code settlementAmount} left null —
     * identical to this method's behavior before external balances existed. When non-empty, an
     * account with no matching entry is omitted entirely — a PHT push only ever covers the
     * accounts it actually carries data for, so an unmatched account has nothing to report this
     * round rather than an "unknown" balance.
     *
     * @param accounts the account assignment rows
     * @param accountBalances externally supplied balances, keyed by account (see {@link
     *     com.example.commander.domain.message.AssemblyContext#accountBalances()})
     * @return list of account allocations
     */
    public List<AccountAllocation> toAccountAllocations(
            List<AccountAssignmentRow> accounts, Map<AccountKey, AccountBalance> accountBalances) {
        if (accountBalances.isEmpty()) {
            return accounts.stream()
                    .map(account -> new AccountAllocation(
                            account.clearingNumber(),
                            account.accountNumber(),
                            account.accountBban(),
                            account.currency()))
                    .toList();
        }
        List<AccountAllocation> result = new ArrayList<>();
        for (AccountAssignmentRow account : accounts) {
            AccountBalance balance =
                    accountBalances.get(new AccountKey(account.clearingNumber(), account.accountNumber()));
            if (balance != null) {
                result.add(new AccountAllocation(
                        account.clearingNumber(),
                        account.accountNumber(),
                        account.accountBban(),
                        account.currency(),
                        balance.balance(),
                        balance.settlementAmount()));
            }
        }
        return result;
    }

    /**
     * Converts alias assignment rows to alias allocations.
     *
     * @param aliases the alias assignment rows
     * @return list of alias allocations
     */
    public List<AliasAllocation> toAliasAllocations(List<AliasAssignmentRow> aliases) {
        return aliases.stream()
                .map(alias -> new AliasAllocation(alias.aliasId()))
                .toList();
    }
}
