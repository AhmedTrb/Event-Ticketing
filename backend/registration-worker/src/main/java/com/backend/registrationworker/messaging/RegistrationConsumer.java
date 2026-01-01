package com.backend.registrationworker.messaging;

import com.backend.registrationworker.service.WorkerBookingService;
import com.backend.shared.messaging.commands.ProcessBookingCommand;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegistrationConsumer {

    private final WorkerBookingService workerBookingService;

    @RabbitListener(queues = "${messaging.rabbitmq.registration-queue}")
    public void consumeRegistrationRequest(ProcessBookingCommand command) {
        log.info("Received registration command for bookingId: {}", command.getBookingId());
        try {
            workerBookingService.processBooking(command);
        } catch (Exception e) {
            log.error("Error processing booking {}", command.getBookingId(), e);
            // In production: Send to Dead Letter Queue (DLQ)
        }
    }
}
