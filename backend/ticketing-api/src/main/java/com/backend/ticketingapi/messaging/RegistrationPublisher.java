package com.backend.ticketingapi.messaging;

import com.backend.shared.messaging.commands.ProcessBookingCommand;
import com.backend.ticketingapi.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegistrationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;

    /**
     * Publishes a booking request to the registration queue.
     * This allows async processing by worker services.
     *
     * @param command The booking command containing user, event, and seat details
     */
    public void publishRegistrationRequest(ProcessBookingCommand command) {
        log.info("Publishing registration request for bookingId: {}", command.getBookingId());

        rabbitTemplate.convertAndSend(
                rabbitMQConfig.getRegistrationExchange(),
                rabbitMQConfig.getRegistrationRoutingKey(),
                command);

        log.info("Published request to queue");
    }
}
