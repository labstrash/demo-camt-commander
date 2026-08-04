package com.example.commander.adapter.message;

import com.example.commander.adapter.message.ondemand.OnDemandMessageListener;
import com.example.commander.adapter.message.ondemand.OnDemandProperties;
import com.example.commander.adapter.message.pht.PhtMessageListener;
import com.example.commander.adapter.message.pht.PhtProperties;
import jakarta.jms.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jms.autoconfigure.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

/**
 * Wires the {@code @JmsListener} container factories for every inbound listener in this
 * application — {@link OnDemandMessageListener} and {@link PhtMessageListener} — from one
 * shared construction helper rather than duplicating it per listener.
 *
 * <p>Each factory bean is still independently {@code @ConditionalOnProperty}-gated (Spring
 * supports this at the {@code @Bean} method level) so the two queues stay independently
 * toggleable — {@code commander.ondemand.enabled}/{@code commander.pht.enabled} — and each
 * keeps its own concurrency setting.
 *
 * <p>Sessions are transacted: when a listener's processing throws, the session rolls back and
 * IBM MQ redelivers the message, incrementing its backout count. Each inbound queue's {@code
 * BOTHRESH}/{@code BOQNAME} (see {@code config.mqsc}) moves it to that queue's backout queue
 * once retries are exhausted — a poison message fails identically on every redelivery and
 * lands there quickly; a transient failure (e.g. a database blip) gets a real chance to
 * succeed on retry first.
 */
@Configuration
public class InboundMqListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(InboundMqListenerConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "commander.ondemand", name = "enabled", havingValue = "true")
    public DefaultJmsListenerContainerFactory onDemandListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            OnDemandProperties properties) {
        return buildFactory(connectionFactory, configurer, properties.getConcurrency(), "on-demand");
    }

    @Bean
    @ConditionalOnProperty(prefix = "commander.pht", name = "enabled", havingValue = "true")
    public DefaultJmsListenerContainerFactory phtListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            PhtProperties properties) {
        return buildFactory(connectionFactory, configurer, properties.getConcurrency(), "PHT");
    }

    private DefaultJmsListenerContainerFactory buildFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            String concurrency,
            String listenerName) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrency(concurrency);
        factory.setSessionTransacted(true);
        factory.setErrorHandler(ex -> log.error("Uncaught error in {} listener container", listenerName, ex));
        return factory;
    }
}
