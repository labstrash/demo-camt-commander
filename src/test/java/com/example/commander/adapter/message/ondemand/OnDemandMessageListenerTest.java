package com.example.commander.adapter.message.ondemand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commander.domain.audit.ReportCommandAuditStatus;
import com.example.commander.domain.ondemand.OnDemandReportRequest;
import com.example.commander.domain.ondemand.OnDemandReportResult;
import com.example.commander.service.OnDemandReportService;
import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.ObjectMessage;
import jakarta.jms.TextMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OnDemandMessageListenerTest {

    @Mock
    private OnDemandReportService onDemandReportService;

    @Mock
    private TextMessage textMessage;

    @Mock
    private BytesMessage bytesMessage;

    @Mock
    private ObjectMessage objectMessage;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesJsonBodyAndTriggersTheService() throws JMSException, UnsupportedEncodingException {
        when(textMessage.getText()).thenReturn("""
                        {
                          "recipientType": "BIC",
                          "recipientValue": "SOMEBIC",
                          "reportType": "CAMT054C",
                          "reportVersion": "1.0",
                          "windowStartUtc": "2026-07-01T00:00:00Z",
                          "windowEndUtc": "2026-07-02T00:00:00Z",
                          "requestorName": "alice"
                        }
                        """);
        when(onDemandReportService.trigger(any()))
                .thenReturn(new OnDemandReportResult(ReportCommandAuditStatus.SENT, null, List.of()));

        new OnDemandMessageListener(objectMapper, onDemandReportService).onMessage(textMessage);

        ArgumentCaptor<OnDemandReportRequest> captor = ArgumentCaptor.forClass(OnDemandReportRequest.class);
        verify(onDemandReportService).trigger(captor.capture());
        OnDemandReportRequest request = captor.getValue();
        assertThat(request.recipientType()).isEqualTo("BIC");
        assertThat(request.recipientValue()).isEqualTo("SOMEBIC");
        assertThat(request.reportType()).isEqualTo("CAMT054C");
        assertThat(request.reportVersion()).isEqualTo("1.0");
        assertThat(request.windowStartUtc()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(request.windowEndUtc()).isEqualTo(Instant.parse("2026-07-02T00:00:00Z"));
        assertThat(request.requestorName()).isEqualTo("alice");
    }

    @Test
    void malformedJsonPropagatesSoTheContainerRollsBackAndMqRedelivers() throws JMSException {
        when(textMessage.getText()).thenReturn("not valid json");
        OnDemandMessageListener listener = new OnDemandMessageListener(objectMapper, onDemandReportService);

        assertThatThrownBy(() -> listener.onMessage(textMessage)).isInstanceOf(RuntimeException.class);

        verify(onDemandReportService, never()).trigger(any());
    }

    @Test
    void emptyBodyIsIgnoredWithoutCallingTheService() throws JMSException, UnsupportedEncodingException {
        when(textMessage.getText()).thenReturn("   ");

        new OnDemandMessageListener(objectMapper, onDemandReportService).onMessage(textMessage);

        verify(onDemandReportService, never()).trigger(any());
    }

    @Test
    void unsupportedMessageTypeIsIgnoredWithoutCallingTheService() throws JMSException, UnsupportedEncodingException {
        new OnDemandMessageListener(objectMapper, onDemandReportService).onMessage(objectMessage);

        verify(onDemandReportService, never()).trigger(any());
    }

    @Test
    void decodesABytesMessageBodyAndTriggersTheService() throws JMSException, UnsupportedEncodingException {
        byte[] bytes = """
                        {
                          "recipientType": "BIC",
                          "recipientValue": "SOMEBIC",
                          "reportType": "CAMT054C",
                          "reportVersion": "1.0",
                          "windowStartUtc": "2026-07-01T00:00:00Z",
                          "windowEndUtc": "2026-07-02T00:00:00Z",
                          "requestorName": "alice"
                        }
                        """.getBytes(StandardCharsets.UTF_8);
        when(bytesMessage.getBodyLength()).thenReturn((long) bytes.length);
        when(bytesMessage.getStringProperty("JMS_IBM_CHARACTER_SET")).thenReturn("UTF-8");
        Answer<Integer> fillBuffer = invocation -> {
            byte[] target = invocation.getArgument(0);
            System.arraycopy(bytes, 0, target, 0, bytes.length);
            return bytes.length;
        };
        when(bytesMessage.readBytes(any(byte[].class), anyInt())).thenAnswer(fillBuffer);
        when(onDemandReportService.trigger(any()))
                .thenReturn(new OnDemandReportResult(ReportCommandAuditStatus.SENT, null, List.of()));

        new OnDemandMessageListener(objectMapper, onDemandReportService).onMessage(bytesMessage);

        ArgumentCaptor<OnDemandReportRequest> captor = ArgumentCaptor.forClass(OnDemandReportRequest.class);
        verify(onDemandReportService).trigger(captor.capture());
        assertThat(captor.getValue().recipientValue()).isEqualTo("SOMEBIC");
    }

    @Test
    void serviceExceptionPropagatesSoTheContainerRollsBackAndMqRedelivers() throws JMSException {
        when(textMessage.getText()).thenReturn("""
                        {
                          "recipientType": "BIC",
                          "recipientValue": "SOMEBIC",
                          "reportType": "CAMT054C",
                          "reportVersion": "1.0",
                          "windowStartUtc": "2026-07-01T00:00:00Z",
                          "windowEndUtc": "2026-07-02T00:00:00Z",
                          "requestorName": "alice"
                        }
                        """);
        when(onDemandReportService.trigger(any())).thenThrow(new RuntimeException("boom"));
        OnDemandMessageListener listener = new OnDemandMessageListener(objectMapper, onDemandReportService);

        assertThatThrownBy(() -> listener.onMessage(textMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }
}
