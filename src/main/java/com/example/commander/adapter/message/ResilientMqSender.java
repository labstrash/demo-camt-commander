package com.example.commander.adapter.message;

import jakarta.jms.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * The one send path both the primary writer and the dead-letter recovery job go through:
 * circuit breaker gate → classify-and-retry transient failures → report the outcome. Neither
 * caller talks to {@link JmsTemplate} directly, and neither implements its own
 * retry/breaker/classification logic — see the MQ resilience decisions this centralizes.
 *
 * <p>Takes the already-serialized payload, not the domain object — the recovery job resends
 * a dead-lettered row's stored {@code message_payload} byte-for-byte, with nothing to
 * (re-)serialize.
 */
@Component
public class ResilientMqSender {

    private static final Logger log = LoggerFactory.getLogger(ResilientMqSender.class);

    private final JmsTemplate jmsTemplate;
    private final MqFailureClassifier classifier;
    private final MqCircuitBreaker circuitBreaker;
    private final RetryTemplate retryTemplate;

    public ResilientMqSender(
            JmsTemplate jmsTemplate,
            MqFailureClassifier classifier,
            MqCircuitBreaker circuitBreaker,
            RetryTemplate mqRetryTemplate) {
        this.jmsTemplate = jmsTemplate;
        this.classifier = classifier;
        this.circuitBreaker = circuitBreaker;
        this.retryTemplate = mqRetryTemplate;
    }

    /**
     * Sends {@code payload} to {@code queue}, gated by the circuit breaker and retrying
     * transient failures up to the configured attempt count.
     *
     * @param queue the target MQ queue name
     * @param payload the already-serialized message body
     * @return the outcome — never throws for a send failure, only reports it
     */
    public SendOutcome send(String queue, String payload) {
        if (!circuitBreaker.isRequestAllowed()) {
            log.warn("Circuit breaker open — skipping send to queue={} without attempting a connection", queue);
            return SendOutcome.breakerOpen();
        }

        try {
            String jmsMessageId = executeSend(queue, payload);
            circuitBreaker.recordSuccess();
            return SendOutcome.success(jmsMessageId);
        } catch (TransientMqFailureException ex) {
            circuitBreaker.recordFailure();
            log.warn("Send to queue={} exhausted every retry attempt", queue, ex.getCause());
            return SendOutcome.transientExhausted(ex.getCause());
        } catch (PermanentMqFailureException ex) {
            log.warn("Send to queue={} failed permanently, not retried", queue, ex.getCause());
            return SendOutcome.permanent(ex.getCause());
        }
    }

    /**
     * Executes the actual JMS send with retry logic and extracts the message ID.
     *
     * @param queue the target MQ queue name
     * @param payload the message payload
     * @return the provider-assigned message ID
     * @throws TransientMqFailureException if all retry attempts are exhausted
     * @throws PermanentMqFailureException if the failure is classified as permanent
     */
    private String executeSend(String queue, String payload) {
        // Captured from the MessageCreator below: after MessageProducer.send() completes,
        // the JMS provider has populated JMSMessageID on this exact Message instance
        // in-place. Reading it here — rather than from convertAndSend(), which returns
        // nothing — is what gives us the real broker-assigned ID.
        MessageWrapper messageWrapper = new MessageWrapper();

        retryTemplate.execute(context -> {
            try {
                jmsTemplate.send(queue, session -> {
                    Message message = session.createTextMessage(payload);
                    messageWrapper.setMessage(message);
                    return message;
                });
            } catch (RuntimeException ex) {
                if (classifier.isTransient(ex)) {
                    throw new TransientMqFailureException(ex);
                }
                throw new PermanentMqFailureException(ex);
            }
            return null;
        });

        return extractJmsMessageId(messageWrapper.getMessage(), queue);
    }

    /**
     * Reads back the provider-assigned message ID. Falls back to {@code null} (rather than
     * failing to send, which already succeeded) if the provider didn't set one — some
     * configurations disable message IDs for performance, though nothing here does that
     * deliberately.
     */
    private static String extractJmsMessageId(Message message, String queue) {
        try {
            return message == null ? null : message.getJMSMessageID();
        } catch (Exception ex) {
            log.warn("Send to queue={} succeeded but JMSMessageID could not be read", queue, ex);
            return null;
        }
    }

    /**
     * Simple holder for the Message object, needed because the Message is created inside
     * the lambda and we need to capture it for ID extraction.
     */
    private static class MessageWrapper {
        private Message message;

        void setMessage(Message message) {
            this.message = message;
        }

        Message getMessage() {
            return message;
        }
    }
}
