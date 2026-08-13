package com.example.commander.domain.message;

/**
 * A reference to a message recipient for report delivery.
 *
 * <p>Contains the essential recipient information needed for outbound messages:
 * delivery type, value, and display name.
 *
 * <p>This is distinct from the requestor (who initiated an on-demand request),
 * which is stored separately for audit and traceability purposes.
 *
 * <p>Deliberately does not carry the recipient's internal DB row id — this type is part
 * of {@link ReportMessage}, which is serialized verbatim onto MQ (and into {@code
 * CAMT.DeadLetterMessage} for retry), so anything on it goes out on the wire. The
 * scheduled pipeline's own use of that id (looking up the real recipient row) lives on
 * {@link ReportMessageEnvelope#recipientId()}/{@link AssemblyContext#recipientId()}
 * instead, which are internal-only and never serialized.
 *
 * @param type recipient type (e.g., SIGNER_ID, BIC)
 * @param value unique identifier of the recipient
 * @param name human-readable display name
 */
public record Recipient(RecipientType type, String value, String name) {
    public Recipient {
        if (type == null) {
            throw new IllegalArgumentException("Recipient type cannot be null");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Recipient value cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipient display name cannot be null or blank");
        }
    }
}
