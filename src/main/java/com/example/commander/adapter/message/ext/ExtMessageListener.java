package com.example.commander.adapter.message.ext;

import com.example.commander.adapter.message.JmsMessageBodyReader;
import com.example.commander.application.ExtReportOrchestrationService;
import com.example.commander.domain.ext.ExtBalanceMessage;
import com.example.commander.domain.ext.ExtMessageParser;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Consumes EXT balance messages from {@code commander.ext.queue}. Extracts the body via
 * {@link JmsMessageBodyReader}, parses it with {@link ExtMessageParser}, and forwards it
 * to {@link ExtReportOrchestrationService} for processing.
 *
 * <p>Unsupported message types or blank bodies are ignored. Any exception thrown during
 * parsing or processing rolls back the transaction, causing redelivery.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "commander.ext", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ExtMessageListener {

    private final ExtMessageParser parser;
    private final ExtReportOrchestrationService orchestrationService;

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
