package com.example.commander.adapter.message.ext;

import com.example.commander.adapter.message.JmsMessageBodyReader;
import com.example.commander.application.ExtReportOrchestrationService;
import com.example.commander.domain.ext.ExtBalanceMessage;
import com.example.commander.domain.ext.ExtMessageParser;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Consumes EXT balance messages from the queue configured by {@code commander.ext.queue},
 * dispatching to {@link ExtMessageParser} then {@link ExtReportOrchestrationService}.
 *
 * <p>Body extraction ({@link jakarta.jms.TextMessage}/{@link jakarta.jms.BytesMessage}, EXT's
 * plain-text format has historically arrived as either) is delegated to {@link
 * JmsMessageBodyReader} — shared with {@link
 * com.example.commander.adapter.message.ondemand.OnDemandMessageListener}.
 *
 * <p>An unsupported JMS message type or a blank body is dropped without dispatching — retrying
 * either can never succeed. Everything else (an unparseable wire format, an unexpected
 * resolution/delivery failure) is left to propagate: the container's transacted session (see
 * {@link com.example.commander.adapter.message.InboundMqListenerConfig}) rolls back and IBM MQ
 * redelivers, eventually backing the message out per {@code CAMT.EXT.QUEUE}'s {@code
 * BOTHRESH}/{@code BOQNAME}.
 */
@Component
@ConditionalOnProperty(prefix = "commander.ext", name = "enabled", havingValue = "true")
public class ExtMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ExtMessageListener.class);

    private final ExtMessageParser parser;
    private final ExtReportOrchestrationService orchestrationService;

    public ExtMessageListener(ExtMessageParser parser, ExtReportOrchestrationService orchestrationService) {
        this.parser = parser;
        this.orchestrationService = orchestrationService;
    }

    @JmsListener(destination = "${commander.ext.queue}", containerFactory = "extListenerContainerFactory")
    public void onMessage(Message message) throws JMSException, UnsupportedEncodingException {
        String body = JmsMessageBodyReader.readBody(message);
        if (body == null) {
            log.warn("Unsupported EXT message type, ignoring: {}", message.getClass());
            return;
        }
        if (body.isBlank()) {
            return;
        }
        ExtBalanceMessage extMessage = parser.parse(body);
        log.info("EXT message received: {}", extMessage);
        orchestrationService.process(extMessage);
    }
}
