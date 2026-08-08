package com.example.commander.adapter.message;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.io.UnsupportedEncodingException;

/**
 * Extracts the text body of an inbound JMS message, shared by every inbound listener in this
 * application ({@code OnDemandMessageListener}, {@code ExtMessageListener}).
 *
 * <p>Handles both {@link TextMessage} and {@link BytesMessage} — a bytes body is decoded using
 * the {@code JMS_IBM_CHARACTER_SET} message property (IBM MQ's own property name for the
 * sender's declared encoding), falling back to UTF-8 if the sender didn't set it. Any other
 * message type is unsupported and returns {@code null} — callers log this with their own
 * listener-specific context.
 */
public final class JmsMessageBodyReader {

    private static final String JMS_IBM_CHARACTER_SET = "JMS_IBM_CHARACTER_SET";
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
        String encoding = message.getStringProperty(JMS_IBM_CHARACTER_SET);
        return new String(bytes, encoding != null ? encoding : DEFAULT_ENCODING).trim();
    }
}
