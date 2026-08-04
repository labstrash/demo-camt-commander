package com.example.commander.adapter.message.pht;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commander.domain.pht.PhtBalanceMessage;
import com.example.commander.service.PhtMessageParser;
import com.example.commander.service.PhtReportOrchestrationService;
import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class PhtMessageListenerTest {

    private static final String SAMPLE =
            "192;01;20260731;173041;062021002635;03;81231;1234564917;4521,94;81231;1234568022;-6768017,24;81231;1234568055;-579660,07";

    @Mock
    private PhtReportOrchestrationService orchestrationService;

    @Mock
    private TextMessage textMessage;

    @Mock
    private BytesMessage bytesMessage;

    private final PhtMessageParser parser = new PhtMessageParser();

    @Test
    void parsesATextMessageAndDispatchesToTheOrchestrationService() throws JMSException, UnsupportedEncodingException {
        when(textMessage.getText()).thenReturn(SAMPLE);

        new PhtMessageListener(parser, orchestrationService).onMessage(textMessage);

        ArgumentCaptor<PhtBalanceMessage> captor = ArgumentCaptor.forClass(PhtBalanceMessage.class);
        verify(orchestrationService).process(captor.capture());
        assertThat(captor.getValue().accountOwner()).isEqualTo("062021002635");
        assertThat(captor.getValue().accounts()).hasSize(3);
    }

    @Test
    void parsesABytesMessageUsingItsDeclaredEncoding() throws JMSException, UnsupportedEncodingException {
        byte[] bytes = SAMPLE.getBytes(StandardCharsets.UTF_8);
        when(bytesMessage.getBodyLength()).thenReturn((long) bytes.length);
        when(bytesMessage.getStringProperty("JMS_IBM_CHARACTER_SET")).thenReturn("UTF-8");
        Answer<Integer> fillBuffer = invocation -> {
            byte[] target = invocation.getArgument(0);
            System.arraycopy(bytes, 0, target, 0, bytes.length);
            return bytes.length;
        };
        when(bytesMessage.readBytes(any(byte[].class), anyInt())).thenAnswer(fillBuffer);

        new PhtMessageListener(parser, orchestrationService).onMessage(bytesMessage);

        verify(orchestrationService).process(any());
    }

    @Test
    void malformedMessagePropagatesSoTheContainerRollsBackAndMqRedelivers() throws JMSException {
        when(textMessage.getText()).thenReturn("not a valid pht message");
        PhtMessageListener listener = new PhtMessageListener(parser, orchestrationService);

        assertThatThrownBy(() -> listener.onMessage(textMessage)).isInstanceOf(IllegalArgumentException.class);

        verify(orchestrationService, never()).process(any());
    }

    @Test
    void emptyBodyIsIgnoredWithoutDispatching() throws JMSException, UnsupportedEncodingException {
        when(textMessage.getText()).thenReturn("   ");

        new PhtMessageListener(parser, orchestrationService).onMessage(textMessage);

        verify(orchestrationService, never()).process(any());
    }

    @Test
    void orchestrationExceptionPropagatesSoTheContainerRollsBackAndMqRedelivers() throws JMSException {
        when(textMessage.getText()).thenReturn(SAMPLE);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(orchestrationService)
                .process(any());
        PhtMessageListener listener = new PhtMessageListener(parser, orchestrationService);

        assertThatThrownBy(() -> listener.onMessage(textMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }
}
