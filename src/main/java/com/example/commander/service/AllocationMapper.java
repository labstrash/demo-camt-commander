package com.example.commander.service;

import com.example.commander.domain.config.AccountAssignmentRow;
import com.example.commander.domain.config.AliasAssignmentRow;
import com.example.commander.domain.message.AccountAllocation;
import com.example.commander.domain.message.AliasAllocation;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps domain assignment rows to message allocation objects.
 */
@Component
public class AllocationMapper {

    /**
     * Converts account assignment rows to account allocations.
     *
     * @param accounts the account assignment rows
     * @return list of account allocations
     */
    public List<AccountAllocation> toAccountAllocations(List<AccountAssignmentRow> accounts) {
        return accounts.stream()
                .map(account -> new AccountAllocation(
                        account.clearingNumber(), account.accountNumber(), account.accountBban(), account.currency()))
                .toList();
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
