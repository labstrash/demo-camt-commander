package com.example.commander.adapter.message.pht;

import com.example.commander.adapter.message.JmsMessageBodyReader;
import com.example.commander.domain.pht.PhtBalanceMessage;
import com.example.commander.service.PhtMessageParser;
import com.example.commander.service.PhtReportOrchestrationService;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Consumes PHT balance messages from the queue configured by {@code commander.pht.queue},
 * dispatching to {@link PhtMessageParser} then {@link PhtReportOrchestrationService}.
 *
 * <p>Body extraction ({@link jakarta.jms.TextMessage}/{@link jakarta.jms.BytesMessage}, PHT's
 * plain-text format has historically arrived as either) is delegated to {@link
 * JmsMessageBodyReader} — shared with {@link
 * com.example.commander.adapter.message.ondemand.OnDemandMessageListener}.
 *
 * <p>An unsupported JMS message type or a blank body is dropped without dispatching — retrying
 * either can never succeed. Everything else (an unparseable wire format, an unexpected
 * resolution/delivery failure) is left to propagate: the container's transacted session (see
 * {@link com.example.commander.adapter.message.InboundMqListenerConfig}) rolls back and IBM MQ
 * redelivers, eventually backing the message out per {@code CAMT.PHT.QUEUE}'s {@code
 * BOTHRESH}/{@code BOQNAME}.
 */
@Component
@ConditionalOnProperty(prefix = "commander.pht", name = "enabled", havingValue = "true")
public class PhtMessageListener {

    private static final Logger log = LoggerFactory.getLogger(PhtMessageListener.class);

    private final PhtMessageParser parser;
    private final PhtReportOrchestrationService orchestrationService;

    public PhtMessageListener(PhtMessageParser parser, PhtReportOrchestrationService orchestrationService) {
        this.parser = parser;
        this.orchestrationService = orchestrationService;
    }

    @JmsListener(destination = "${commander.pht.queue}", containerFactory = "phtListenerContainerFactory")
    public void onMessage(Message message) throws JMSException, UnsupportedEncodingException {
        String body = JmsMessageBodyReader.readBody(message);
        if (body == null) {
            log.warn("Unsupported PHT message type, ignoring: {}", message.getClass());
            return;
        }
        if (body.isBlank()) {
            return;
        }
        PhtBalanceMessage phtMessage = parser.parse(body);
        log.info("PHT message received: {}", phtMessage);
        orchestrationService.process(phtMessage);
    }
}
