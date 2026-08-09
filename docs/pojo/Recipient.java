package com.example.camt.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The message's delivery recipient.
 *
 * @param id the recipient's surrogate identifier in the sending system
 * @param type recipient type (e.g. SIGNER_ID, BIC)
 * @param value the recipient's delivery address/endpoint value
 * @param name human-readable display name
 */
public record Recipient(
        @JsonProperty("id") long id,
        @JsonProperty("type") RecipientType type,
        @JsonProperty("value") String value,
        @JsonProperty("name") String name) {}
