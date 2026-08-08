package com.example.commander.domain.assembly;

import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AssemblyContext;
import com.example.commander.domain.message.PaymentTypeAllocation;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ScopedAllocation;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Assembles report configuration trees into outbound messages.
 *
 * <p>Transforms a {@link ReportConfigTree} into one or more {@link ReportExecutionEnvelope}s
 * based on the configuration's bundling rules and scope assignments.
 *
 * <p>Three assembly strategies:
 * <ol>
 *   <li><b>Zero scopes:</b> Single config-only message with no account/alias data</li>
 *   <li><b>Bundled ({@code isBundled = true}):</b> One message per config, merging all
 *       accounts and aliases across all scopes and payment types</li>
 *   <li><b>Unbundled ({@code isBundled = false}):</b> One message per account or alias row</li>
 * </ol>
 *
 * <p>This service is pure Java with no database dependencies, making it easily testable
 * without a database or Spring context.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReportMessageAssembler {
    private final OutboundMessageBuilder messageBuilder;
    private final MessageGroupingStrategyFactory strategyFactory;

    /**
     * Assembles a configuration tree into outbound messages.
     *
     * @param tree the assembled report configuration tree
     * @param context the message assembly context
     * @return list of pipeline messages (one or more based on bundling rules)
     * @throws NullPointerException if tree or context is null
     */
    public List<ReportMessageEnvelope> assemble(ReportConfigTree tree, AssemblyContext context) {
        validateInputs(tree, context);
        log.debug(
                "Assembling messages for configId={}, bundled={}, scopes={}",
                tree.config().configId(),
                tree.config().isBundled(),
                tree.scopes().size());

        if (tree.isZeroScope()) {
            log.debug("Zero scopes detected, creating config-only message");
            return List.of(createConfigOnlyMessage(tree, context));
        }

        return createMessages(tree, context);
    }

    private List<ReportMessageEnvelope> createMessages(ReportConfigTree tree, AssemblyContext context) {
        MessageGroupingStrategy strategy = strategyFactory.forConfig(tree.config());
        List<ScopedAllocation> groups = strategy.group(tree, context.accountBalances());

        if (tree.config().isBundled()) {
            log.debug("Creating bundled message with {} payment types", groups.size());
            List<PaymentTypeAllocation> allocations =
                    groups.stream().map(ScopedAllocation::allocation).toList();
            return List.of(createSingleMessage(tree, context, null, allocations));
        }

        log.debug("Creating {} unbundled messages", groups.size());
        return groups.stream()
                .map(group -> createSingleMessage(tree, context, group.scopeId(), List.of(group.allocation())))
                .toList();
    }

    private ReportMessageEnvelope createSingleMessage(
            ReportConfigTree tree, AssemblyContext context, Long scopeId, List<PaymentTypeAllocation> groups) {

        return messageBuilder.build(tree, context, scopeId, groups);
    }

    private ReportMessageEnvelope createConfigOnlyMessage(ReportConfigTree tree, AssemblyContext context) {
        return messageBuilder.build(tree, context, null, List.of());
    }

    private void validateInputs(ReportConfigTree tree, AssemblyContext context) {
        Objects.requireNonNull(tree, "tree cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
    }
}
