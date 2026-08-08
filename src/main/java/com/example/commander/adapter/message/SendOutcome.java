package com.example.commander.adapter.message;

/**
 * Result of a {@link ResilientMqSender#send(String, String)} call.
 *
 * @param type         the outcome type (see {@link Type})
 * @param cause        the failure cause, or {@code null} for {@link Type#SUCCESS}
 * @param jmsMessageId the provider-assigned message ID, or {@code null} for non‑{@code SUCCESS}
 */
public record SendOutcome(Type type, Throwable cause, String jmsMessageId) {

    /**
     * Outcome types for an MQ send attempt.
     */
    public enum Type {
        /** Message was sent successfully. */
        SUCCESS,
        /** Circuit breaker was OPEN – no connection attempt was made. */
        BREAKER_OPEN,
        /** All retry attempts were exhausted; the failure remains transient but unrecoverable. */
        TRANSIENT_EXHAUSTED,
        /** Failure was classified permanent (e.g., invalid destination); not retried. */
        PERMANENT
    }

    public static SendOutcome success(String jmsMessageId) {
        return new SendOutcome(Type.SUCCESS, null, jmsMessageId);
    }

    public static SendOutcome breakerOpen() {
        return new SendOutcome(Type.BREAKER_OPEN, null, null);
    }

    public static SendOutcome transientExhausted(Throwable cause) {
        return new SendOutcome(Type.TRANSIENT_EXHAUSTED, cause, null);
    }

    public static SendOutcome permanent(Throwable cause) {
        return new SendOutcome(Type.PERMANENT, cause, null);
    }

    /**
     * @return {@code true} if the message was not delivered (any type except {@code SUCCESS})
     */
    public boolean isFailure() {
        return type != Type.SUCCESS;
    }
}
