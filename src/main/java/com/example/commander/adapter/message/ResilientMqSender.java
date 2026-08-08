package com.example.commander.adapter.message;

import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Central outbound MQ sender with a three‑step resilience pipeline — the one send path both
 * the primary writer and the dead-letter recovery job go through, so neither implements its
 * own retry/breaker/classification logic:
 *
 * <ol>
 *   <li><b>Circuit Breaker</b> – {@link MqCircuitBreaker} gates all sends.
 *       When OPEN, calls are rejected immediately (no connection attempt). A probe call
 *       is allowed after the cooldown – success closes the breaker; failure re‑opens it.
 *       See {@link MqCircuitBreaker} for state definitions (CLOSED, OPEN, HALF‑OPEN).</li>
 *   <li><b>Failure Classification</b> – {@link MqFailureClassifier} determines if a failure
 *       is transient (retriable) or permanent (not retried).</li>
 *   <li><b>In‑process Retry</b> – Transient failures are retried using {@link RetryTemplate}
 *       with configured attempts and fixed backoff. If all attempts fail, the failure is
 *       reported as {@link SendOutcome.Type#TRANSIENT_EXHAUSTED}.</li>
 * </ol>
 *
 * <p>Like {@link MqCircuitBreaker}, the pipeline is hand-rolled rather than resilience4j —
 * {@code RetryTemplate} (Spring Retry) already covers step 3, and the breaker is small enough
 * not to need a library of its own.
 *
 * <p>Takes the already-serialized payload, not the domain object — the recovery job resends a
 * dead-lettered row's stored {@code message_payload} byte-for-byte, with nothing left to
 * (re-)serialize.
 *
 * <p>Successful sends record a success with the circuit breaker and return the provider‑assigned
 * JMS message ID. This method <b>never throws</b> – all outcomes are captured in {@link SendOutcome}.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ResilientMqSender {

    private final JmsTemplate jmsTemplate;
    private final MqFailureClassifier classifier;
    private final MqCircuitBreaker circuitBreaker;
    private final RetryTemplate retryTemplate;

    /**
     * Sends a payload to an MQ queue with full resilience.
     *
     * @param queue   target queue name
     * @param payload already‑serialized message body
     * @return outcome (never {@code null})
     */
    public SendOutcome send(String queue, String payload) {
        if (!circuitBreaker.isRequestAllowed()) {
            log.warn("Circuit breaker open – skipping send to queue={}", queue);
            return SendOutcome.breakerOpen();
        }

        try {
            String jmsMessageId = executeSend(queue, payload);
            circuitBreaker.recordSuccess();
            return SendOutcome.success(jmsMessageId);
        } catch (TransientMqFailureException ex) {
            circuitBreaker.recordFailure();
            log.warn("Send to queue={} exhausted all retry attempts", queue, ex.getCause());
            return SendOutcome.transientExhausted(ex.getCause());
        } catch (PermanentMqFailureException ex) {
            log.warn("Send to queue={} failed permanently, not retried", queue, ex.getCause());
            return SendOutcome.permanent(ex.getCause());
        }
    }

    /**
     * Runs one retry-governed send attempt cycle and returns the resulting JMS message ID.
     *
     * @throws TransientMqFailureException if every retry attempt is exhausted
     * @throws PermanentMqFailureException if the failure is classified permanent (never retried)
     */
    private String executeSend(String queue, String payload) {
        MessageWrapper wrapper = new MessageWrapper();

        retryTemplate.execute(context -> {
            try {
                jmsTemplate.send(queue, session -> {
                    Message message = session.createTextMessage(payload);
                    wrapper.setMessage(message);
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

        return extractJmsMessageId(wrapper.getMessage(), queue);
    }

    /**
     * Reads back the provider-assigned message ID, tolerating a provider that can't supply one.
     * Falls back to {@code null} rather than failing the send, which already succeeded.
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
     * Escapes the {@link Message} created inside {@code jmsTemplate.send}'s lambda out to
     * {@link #executeSend}, so its {@code JMSMessageID} — populated on that exact object
     * in-place by the provider once the send completes — can be read afterward. {@code
     * JmsTemplate.send(...)} itself returns {@code void}, and {@code convertAndSend(...)}
     * doesn't hand back the {@link Message} either, so this is the only way to recover the
     * ID without a second round-trip to the broker.
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
