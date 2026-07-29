package com.example.commander.domain.message;

/**
 * A reference to a message recipient for report delivery.
 *
 * <p>Contains the essential recipient information needed for outbound messages:
 * delivery type, address, and display name.
 *
 * <p>This is distinct from the requestor (who initiated an on-demand request),
 * which is stored separately for audit and traceability purposes.
 *
 * @param id unique identifier of the recipient
 * @param type recipient type (e.g., ORIGINATOR, BIC)
 * @param address unique identifier of the recipient
 * @param displayName human-readable display name
 */
public record Recipient(long id, RecipientType type, String address, String displayName) {
    public Recipient {
        if (id <= 0) {
            throw new IllegalArgumentException("Recipient id must be positive");
        }
        if (type == null) {
            throw new IllegalArgumentException("Recipient type cannot be null");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Recipient address cannot be null or blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Recipient display name cannot be null or blank");
        }
    }
}
