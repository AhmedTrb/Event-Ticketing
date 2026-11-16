package com.backend.registrationworker.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${messaging.rabbitmq.registration-queue}")
    private String registrationQueue;

    /**
     * Declared here as well (idempotent, same durable definition as ticketing-api)
     * so the worker can start before the API without failing on a missing queue.
     */
    @Bean
    public Queue registrationQueue() {
        return new Queue(registrationQueue, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
