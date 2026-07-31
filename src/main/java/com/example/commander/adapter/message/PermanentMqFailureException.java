package com.example.commander.adapter.message;

/**
 * Internal marker wrapping a send failure {@link MqFailureClassifier} classified permanent —
 * excluded from {@link ResilientMqSender}'s {@code RetryTemplate} retry scope, so a
 * permanent failure is sent once and routed straight to dead-letter.
 */
public class PermanentMqFailureException extends RuntimeException {

    public PermanentMqFailureException(Throwable cause) {
        super(cause);
    }
}
