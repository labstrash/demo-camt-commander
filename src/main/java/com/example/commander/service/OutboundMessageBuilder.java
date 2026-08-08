package com.example.commander.service;

import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AssemblyContext;
import com.example.commander.domain.message.PaymentTypeAllocation;
import com.example.commander.domain.message.ReportContext;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportMessageIdGenerator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds outbound report messages from configuration trees and context.
 */
@Component
public class OutboundMessageBuilder {
    private final CorrelationIdGenerator correlationIdGenerator;
    private final ReportMessageIdGenerator messageIdGenerator;
    private final MessageIdValidator messageIdValidator;

    public OutboundMessageBuilder(
            CorrelationIdGenerator correlationIdGenerator,
            ReportMessageIdGenerator messageIdGenerator,
            MessageIdValidator messageIdValidator) {
        this.correlationIdGenerator = correlationIdGenerator;
        this.messageIdGenerator = messageIdGenerator;
        this.messageIdValidator = messageIdValidator;
    }

    /**
     * Builds a pipeline report message.
     *
     * @param tree the report configuration tree
     * @param context the assembly context
     * @param scopeId the scope ID (null for bundled/config-only)
     * @param paymentTypeGroups the payment type groups for this message
     * @return the built pipeline message
     */
    public ReportMessageEnvelope build(
            ReportConfigTree tree,
            AssemblyContext context,
            Long scopeId,
            List<PaymentTypeAllocation> paymentTypeGroups) {

        ReportConfigRow config = tree.config();
        ReportContext reportContext = context.reportContext();

        // Generate IDs
        String correlationId = correlationIdGenerator.generate(
                config.id(),
                scopeId,
                reportContext.window().windowStartUtc(),
                reportContext.window().windowEndUtc(),
                paymentTypeGroups);

        String messageId = messageIdGenerator.generateMessageId(config.configId(), config.reportType());
        messageIdValidator.validate(messageId, config.configId(), config.reportType());

        // Build payload
        ReportMessage payload = ReportMessage.builder()
                .reportId(config.configId())
                .type(config.reportType())
                .version(reportContext.reportVersion())
                .windowStartUtc(reportContext.window().windowStartUtc())
                .windowEndUtc(reportContext.window().windowEndUtc())
                .bundled(scopeId == null)
                .accountFormat(config.accountFormat())
                .isPaginated(config.isPaginated())
                .isEmptyReportAllowed(config.isEmptyReportAllowed())
                .triggerType(reportContext.triggerType())
                .recipient(context.recipient())
                .paymentTypeGroups(paymentTypeGroups)
                .requestorName(context.requestorName())
                .correlationId(correlationId)
                .id(messageId)
                .build();

        // Build envelope
        return new ReportMessageEnvelope(payload, config.id(), scopeId);
    }
}
