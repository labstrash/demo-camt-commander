package com.example.commander.adapter.message;

/**
 * Marks a send failure that is not worth retrying. Wraps exceptions classified as
 * permanent by {@link MqFailureClassifier} (e.g., invalid destination).
 *
 * <p>This exception is excluded from {@link ResilientMqSender}'s retry scope.
 */
public class PermanentMqFailureException extends RuntimeException {

    public PermanentMqFailureException(Throwable cause) {
        super(cause);
    }
}
