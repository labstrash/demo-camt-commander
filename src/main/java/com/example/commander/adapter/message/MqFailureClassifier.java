package com.example.commander.adapter.message;

import org.springframework.jms.InvalidDestinationException;
import org.springframework.jms.JmsException;
import org.springframework.stereotype.Component;

/**
 * Classifies MQ send failures as <b>transient</b> or <b>permanent</b>.
 *
 * <p><b>Transient</b> – a failure that may succeed if retried later. Examples:
 * <ul>
 *   <li>Network timeouts</li>
 *   <li>Connection resets</li>
 *   <li>Broker temporarily unavailable</li>
 * </ul>
 * These are typically {@link JmsException} subclasses that indicate a temporary
 * communication or resource issue.
 *
 * <p><b>Permanent</b> – a failure that will never succeed on retry because the
 * message itself is invalid or the destination is misconfigured. Examples:
 * <ul>
 *   <li>{@link InvalidDestinationException} – the queue does not exist</li>
 *   <li>Any non‑{@link JmsException} (unrecognized failure) – treated as permanent
 *       as a safe default; retrying an unknown error is risky</li>
 * </ul>
 */
@Component
public class MqFailureClassifier {

    /**
     * Determines whether a send failure is transient (retriable) or permanent.
     *
     * @param ex the exception thrown by the send attempt
     * @return {@code true} if the failure is transient and worth retrying;
     *         {@code false} if permanent
     */
    public boolean isTransient(Throwable ex) {
        if (ex instanceof InvalidDestinationException) {
            return false;
        }
        return ex instanceof JmsException;
    }
}
