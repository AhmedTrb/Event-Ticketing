package com.backend.ticketingapi.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitMQConfig {

    @Value("${messaging.rabbitmq.registration-queue}")
    private String registrationQueue;

    @Value("${messaging.rabbitmq.registration-exchange}")
    private String registrationExchange;

    @Value("${messaging.rabbitmq.registration-routing-key}")
    private String registrationRoutingKey;

    @Bean
    public Queue registrationQueue() {
        return new Queue(registrationQueue, true);
    }

    @Bean
    public TopicExchange registrationExchange() {
        return new TopicExchange(registrationExchange);
    }

    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(registrationRoutingKey);
    }

    public String getRegistrationExchange() {
        return registrationExchange;
    }

    public String getRegistrationRoutingKey() {
        return registrationRoutingKey;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
