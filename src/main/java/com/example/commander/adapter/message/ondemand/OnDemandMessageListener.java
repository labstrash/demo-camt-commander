package com.example.commander.adapter.message.ondemand;

import com.example.commander.adapter.message.JmsMessageBodyReader;
import com.example.commander.application.OnDemandReportService;
import com.example.commander.domain.ondemand.OnDemandReportRequest;
import com.example.commander.domain.ondemand.OnDemandReportResult;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes on-demand report requests from {@code CAMT.ONDEMAND.QUEUE} — a JSON body
 * deserializing directly to {@link OnDemandReportRequest}, dispatched to {@link
 * OnDemandReportService#trigger}.
 *
 * <p>Fire-and-forget: no reply-to, no response of any kind back to the sender. The outcome is
 * logged here and durably recorded as a {@code CAMT.ReportCommandAudit} row by the service —
 * same posture as every other send path in this application.
 *
 * <p>An unsupported JMS message type or a blank body is dropped without dispatching — retrying
 * either can never succeed. Everything else (malformed JSON, an unexpected failure from {@link
 * OnDemandReportService#trigger}) is left to propagate: the container's transacted session (see
 * {@link com.example.commander.adapter.message.InboundMqListenerConfig}) rolls back and IBM MQ
 * redelivers, eventually backing the message out per {@code CAMT.ONDEMAND.QUEUE}'s {@code
 * BOTHRESH}/{@code BOQNAME}.
 */
@Component
@ConditionalOnProperty(prefix = "commander.ondemand", name = "enabled", havingValue = "true")
public class OnDemandMessageListener {

    private static final Logger log = LoggerFactory.getLogger(OnDemandMessageListener.class);

    private final ObjectMapper objectMapper;
    private final OnDemandReportService onDemandReportService;

    public OnDemandMessageListener(ObjectMapper objectMapper, OnDemandReportService onDemandReportService) {
        this.objectMapper = objectMapper;
        this.onDemandReportService = onDemandReportService;
    }

    @JmsListener(destination = "${commander.ondemand.queue}", containerFactory = "onDemandListenerContainerFactory")
    public void onMessage(Message message) throws JMSException, UnsupportedEncodingException {
        String body = JmsMessageBodyReader.readBody(message);
        if (body == null) {
            log.warn("Unsupported on-demand message type, ignoring: {}", message.getClass());
            return;
        }
        if (body.isBlank()) {
            return;
        }
        OnDemandReportRequest request = objectMapper.readValue(body, OnDemandReportRequest.class);
        log.info("On-demand request received: {}", request);
        OnDemandReportResult result = onDemandReportService.trigger(request);
        log.info(
                "On-demand request processed: status={}, detail={}, messageCount={}",
                result.status(),
                result.detail(),
                result.messages().size());
    }
}
