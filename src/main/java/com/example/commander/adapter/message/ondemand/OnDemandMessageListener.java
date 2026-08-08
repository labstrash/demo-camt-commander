package com.example.commander.adapter.message.ondemand;

import com.example.commander.adapter.message.JmsMessageBodyReader;
import com.example.commander.application.OnDemandReportService;
import com.example.commander.domain.ondemand.OnDemandReportRequest;
import com.example.commander.domain.ondemand.OnDemandReportResult;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes on‑demand report requests from {@code commander.ondemand.queue}. Extracts the JSON
 * body via {@link JmsMessageBodyReader}, deserializes it to {@link OnDemandReportRequest}, and
 * delegates to {@link OnDemandReportService#trigger(OnDemandReportRequest)}.
 *
 * <p>Unsupported message types or blank bodies are ignored. Any exception during processing
 * rolls back the transaction, causing redelivery. The result is logged only; no response is
 * sent back to the requester.
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(prefix = "commander.ondemand", name = "enabled", havingValue = "true")
public class OnDemandMessageListener {

    private final ObjectMapper objectMapper;
    private final OnDemandReportService onDemandReportService;

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
