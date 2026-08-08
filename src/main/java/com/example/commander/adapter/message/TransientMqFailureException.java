package com.example.commander.adapter.message;

/**
 * Marks a send failure that may succeed if retried. Wraps exceptions classified as
 * transient by {@link MqFailureClassifier} (e.g., network timeout, connection reset).
 *
 * <p>This is the only exception type retried by {@link ResilientMqSender}'s
 * {@code RetryTemplate}.
 */
public class TransientMqFailureException extends RuntimeException {

    public TransientMqFailureException(Throwable cause) {
        super(cause);
    }
}
