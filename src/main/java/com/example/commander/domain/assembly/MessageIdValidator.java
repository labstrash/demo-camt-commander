package com.example.commander.domain.assembly;

import com.example.commander.domain.message.ReportType;
import org.springframework.stereotype.Component;

/**
 * Validates generated message IDs against constraints.
 */
@Component
public class MessageIdValidator {

    private static final int MAX_MESSAGE_ID_LENGTH = 35;

    /**
     * Validates a message ID against length constraints.
     *
     * <p>{@code MAX_MESSAGE_ID_LENGTH} (35) is inclusive — a message ID of exactly 35
     * characters is valid; only 36+ is rejected.
     *
     * @param messageId the message ID to validate
     * @param configId the business config ID the message ID was generated for (diagnostic context)
     * @param reportType the report type the message ID was generated for (diagnostic context)
     * @throws IllegalStateException if the message ID is invalid
     */
    public void validate(String messageId, int configId, ReportType reportType) {
        if (messageId == null) {
            throw new IllegalStateException("Message ID cannot be null");
        }

        if (messageId.length() > MAX_MESSAGE_ID_LENGTH) {
            throw new IllegalStateException(String.format(
                    "Message ID length %d exceeds maximum %d for reportType=%s, configId=%d: %s",
                    messageId.length(), MAX_MESSAGE_ID_LENGTH, reportType, configId, messageId));
        }
    }
}
