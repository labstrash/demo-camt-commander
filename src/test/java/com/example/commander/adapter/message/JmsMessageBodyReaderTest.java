package com.example.commander.adapter.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.ObjectMessage;
import jakarta.jms.TextMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class JmsMessageBodyReaderTest {

    @Mock
    private TextMessage textMessage;

    @Mock
    private BytesMessage bytesMessage;

    @Mock
    private ObjectMessage objectMessage;

    @Test
    void readsATextMessageBodyDirectly() throws JMSException, UnsupportedEncodingException {
        when(textMessage.getText()).thenReturn("hello");

        String body = JmsMessageBodyReader.readBody(textMessage);

        assertThat(body).isEqualTo("hello");
    }

    @Test
    void decodesABytesMessageUsingItsDeclaredCharacterSet() throws JMSException, UnsupportedEncodingException {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        stubBytesMessage(bytes, "UTF-8");

        String body = JmsMessageBodyReader.readBody(bytesMessage);

        assertThat(body).isEqualTo("hello");
    }

    @Test
    void fallsBackToUtf8WhenNoCharacterSetPropertyIsSet() throws JMSException, UnsupportedEncodingException {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        stubBytesMessage(bytes, null);

        String body = JmsMessageBodyReader.readBody(bytesMessage);

        assertThat(body).isEqualTo("hello");
    }

    @Test
    void trimsTheDecodedBytesBody() throws JMSException, UnsupportedEncodingException {
        byte[] bytes = "  hello  ".getBytes(StandardCharsets.UTF_8);
        stubBytesMessage(bytes, "UTF-8");

        String body = JmsMessageBodyReader.readBody(bytesMessage);

        assertThat(body).isEqualTo("hello");
    }

    @Test
    void returnsNullForAnUnsupportedMessageType() throws JMSException, UnsupportedEncodingException {
        String body = JmsMessageBodyReader.readBody(objectMessage);

        assertThat(body).isNull();
    }

    private void stubBytesMessage(byte[] bytes, String characterSet) throws JMSException, UnsupportedEncodingException {
        when(bytesMessage.getBodyLength()).thenReturn((long) bytes.length);
        when(bytesMessage.getStringProperty("JMS_IBM_CHARACTER_SET")).thenReturn(characterSet);
        Answer<Integer> fillBuffer = invocation -> {
            byte[] target = invocation.getArgument(0);
            System.arraycopy(bytes, 0, target, 0, bytes.length);
            return bytes.length;
        };
        when(bytesMessage.readBytes(any(byte[].class), anyInt())).thenAnswer(fillBuffer);
    }
}
