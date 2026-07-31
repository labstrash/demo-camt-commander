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
 * @param id unique identifier of the recipient
 * @param type recipient type (e.g., ORIGINATOR, BIC)
 * @param value unique identifier of the recipient
 * @param name human-readable display name
 */
public record Recipient(long id, RecipientType type, String value, String name) {
    public Recipient {
        if (id <= 0) {
            throw new IllegalArgumentException("Recipient id must be positive");
        }
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
