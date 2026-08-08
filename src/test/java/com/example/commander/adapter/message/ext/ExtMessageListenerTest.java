package com.example.commander.adapter.message.ext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commander.application.ExtReportOrchestrationService;
import com.example.commander.domain.ext.ExtBalanceMessage;
import com.example.commander.domain.ext.ExtMessageParser;
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
class ExtMessageListenerTest {

    private static final String SAMPLE = "192;01;20260731;173041;062021002635;03;"
            + "81231;1234564917;4521,94;4521,94;"
            + "81231;1234568022;-6768017,24;-6768017,24;"
            + "81231;1234568055;-579660,07;-579660,07";

    @Mock
    private ExtReportOrchestrationService orchestrationService;

    @Mock
    private TextMessage textMessage;

    @Mock
    private BytesMessage bytesMessage;

    private final ExtMessageParser parser = new ExtMessageParser();

    @Test
    void parsesATextMessageAndDispatchesToTheOrchestrationService() throws JMSException, UnsupportedEncodingException {
        when(textMessage.getText()).thenReturn(SAMPLE);

        new ExtMessageListener(parser, orchestrationService).onMessage(textMessage);

        ArgumentCaptor<ExtBalanceMessage> captor = ArgumentCaptor.forClass(ExtBalanceMessage.class);
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

        new ExtMessageListener(parser, orchestrationService).onMessage(bytesMessage);

        verify(orchestrationService).process(any());
    }

    @Test
    void malformedMessagePropagatesSoTheContainerRollsBackAndMqRedelivers() throws JMSException {
        when(textMessage.getText()).thenReturn("not a valid ext message");
        ExtMessageListener listener = new ExtMessageListener(parser, orchestrationService);

        assertThatThrownBy(() -> listener.onMessage(textMessage)).isInstanceOf(IllegalArgumentException.class);

        verify(orchestrationService, never()).process(any());
    }

    @Test
    void emptyBodyIsIgnoredWithoutDispatching() throws JMSException, UnsupportedEncodingException {
        when(textMessage.getText()).thenReturn("   ");

        new ExtMessageListener(parser, orchestrationService).onMessage(textMessage);

        verify(orchestrationService, never()).process(any());
    }

    @Test
    void orchestrationExceptionPropagatesSoTheContainerRollsBackAndMqRedelivers() throws JMSException {
        when(textMessage.getText()).thenReturn(SAMPLE);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(orchestrationService)
                .process(any());
        ExtMessageListener listener = new ExtMessageListener(parser, orchestrationService);

        assertThatThrownBy(() -> listener.onMessage(textMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }
}
