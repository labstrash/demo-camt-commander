package com.example.commander.adapter.message;

import com.example.commander.adapter.message.ext.ExtProperties;
import com.example.commander.adapter.message.ondemand.OnDemandProperties;
import jakarta.jms.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jms.autoconfigure.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

/**
 * Configures {@code @JmsListener} container factories for inbound queues.
 * Each factory is gated by its corresponding {@code enabled} property and uses the
 * specified concurrency. Sessions are transacted, so exceptions cause redelivery.
 */
@Slf4j
@Configuration
public class InboundMqListenerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "commander.ondemand", name = "enabled", havingValue = "true")
    public DefaultJmsListenerContainerFactory onDemandListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            OnDemandProperties properties) {
        return buildFactory(connectionFactory, configurer, properties.getConcurrency(), "on-demand");
    }

    @Bean
    @ConditionalOnProperty(prefix = "commander.ext", name = "enabled", havingValue = "true")
    public DefaultJmsListenerContainerFactory extListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            ExtProperties properties) {
        return buildFactory(connectionFactory, configurer, properties.getConcurrency(), "EXT");
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
