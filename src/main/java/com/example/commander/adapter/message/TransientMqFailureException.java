package com.example.commander.adapter.message;

/**
 * Internal marker wrapping a send failure {@link MqFailureClassifier} classified transient —
 * exists solely to scope {@link ResilientMqSender}'s {@code RetryTemplate} to retrying only
 * this type, not every exception a send attempt could throw.
 */
public class TransientMqFailureException extends RuntimeException {

    public TransientMqFailureException(Throwable cause) {
        super(cause);
    }
}
