package com.backend.shared;

import com.backend.shared.exceptions.BusinessException;
import com.backend.shared.exceptions.InvalidBookingException;
import com.backend.shared.exceptions.SeatAlreadyBookedException;
import com.backend.shared.exceptions.SeatLockExpiredException;
import com.backend.shared.messaging.MessageEnvelope;
import com.backend.shared.messaging.commands.ProcessBookingCommand;
import com.backend.shared.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedContractsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("ProcessBookingCommand survives a JSON round trip (API -> RabbitMQ -> worker)")
    void command_jsonRoundTrip() throws Exception {
        ProcessBookingCommand original = new ProcessBookingCommand(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of("A1", "B2"), true);

        ProcessBookingCommand copy = mapper.readValue(mapper.writeValueAsString(original),
                ProcessBookingCommand.class);

        assertThat(copy).isEqualTo(original);
        assertThat(copy.getSelectedSeats()).containsExactly("A1", "B2");
        assertThat(copy.isFromQueue()).isTrue();
    }

    @Test
    @DisplayName("MessageEnvelope.create generates ids and records the payload type")
    void envelope_create() {
        ProcessBookingCommand payload = new ProcessBookingCommand(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of("A1"), false);

        MessageEnvelope<ProcessBookingCommand> envelope = MessageEnvelope.create(payload);

        assertThat(envelope.getMessageId()).isNotNull();
        assertThat(envelope.getCorrelationId()).isNotNull();
        assertThat(envelope.getTimestamp()).isNotNull();
        assertThat(envelope.getPayloadType()).isEqualTo(ProcessBookingCommand.class.getName());
        assertThat(envelope.getPayload()).isSameAs(payload);

        UUID correlation = UUID.randomUUID();
        assertThat(MessageEnvelope.create(payload, correlation).getCorrelationId()).isEqualTo(correlation);
    }

    @Test
    @DisplayName("domain exceptions expose stable error codes")
    void exceptions_errorCodes() {
        UUID eventId = UUID.randomUUID();
        assertThat(new SeatAlreadyBookedException(eventId, "A1").getErrorCode()).isEqualTo("SEAT_ALREADY_BOOKED");
        assertThat(new SeatAlreadyBookedException(eventId, "A1").getUserMessage()).contains("A1");
        assertThat(new SeatLockExpiredException("x").getErrorCode()).isEqualTo("SEAT_LOCK_EXPIRED");
        assertThat(new InvalidBookingException("bad")).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("INVALID_BOOKING");
    }

    @Test
    @DisplayName("IdGenerator produces unique, parseable UUIDs")
    void idGenerator() {
        assertThat(IdGenerator.generate()).isNotEqualTo(IdGenerator.generate());
        UUID id = IdGenerator.generate();
        assertThat(IdGenerator.fromString(IdGenerator.generateString())).isNotNull();
        assertThat(IdGenerator.fromString(id.toString())).isEqualTo(id);
        assertThatThrownBy(() -> IdGenerator.fromString("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
