package com.example.commander.adapter.message;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.io.UnsupportedEncodingException;

/**
 * Extracts the text body from a JMS message. Supports {@link TextMessage} and
 * {@link BytesMessage}. Bytes messages are decoded using the {@code JMS_IBM_CHARACTER_SET}
 * property if present, falling back to UTF-8. Unsupported types return {@code null}.
 */
public final class JmsMessageBodyReader {

    private static final String IBM_CHARSET_PROPERTY = "JMS_IBM_CHARACTER_SET";
    private static final String DEFAULT_ENCODING = "UTF-8";

    private JmsMessageBodyReader() {}

    public static String readBody(Message message) throws JMSException, UnsupportedEncodingException {
        if (message instanceof TextMessage textMessage) {
            return textMessage.getText();
        }
        if (message instanceof BytesMessage bytesMessage) {
            return readBytesMessage(bytesMessage);
        }
        return null;
    }

    private static String readBytesMessage(BytesMessage message) throws JMSException, UnsupportedEncodingException {
        message.reset();
        int length = (int) message.getBodyLength();
        byte[] bytes = new byte[length];
        message.readBytes(bytes, length);
        String encoding = message.getStringProperty(IBM_CHARSET_PROPERTY);
        return new String(bytes, encoding != null ? encoding : DEFAULT_ENCODING).trim();
    }
}
