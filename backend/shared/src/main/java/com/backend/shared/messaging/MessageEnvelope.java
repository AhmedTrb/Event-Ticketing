package com.backend.shared.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic wrapper for all messages in the event-driven system.
 * Provides metadata and traceability for messages sent between services.
 * 
 * @param <T> The type of the payload
 */
@Getter
@ToString
@EqualsAndHashCode
public class MessageEnvelope<T> {

    /**
     * Unique identifier for this message
     */
    private final UUID messageId;

    /**
     * Correlation ID for tracing related messages
     */
    private final UUID correlationId;

    /**
     * When the message was created
     */
    private final Instant timestamp;

    /**
     * The type of the payload (fully qualified class name)
     */
    private final String payloadType;

    /**
     * The actual message payload
     */
    private final T payload;

    /**
     * Creates a new MessageEnvelope
     * 
     * @param messageId     Unique message ID
     * @param correlationId Correlation ID for tracing
     * @param timestamp     Message creation time
     * @param payloadType   Payload type name
     * @param payload       The actual payload
     */
    @JsonCreator
    public MessageEnvelope(
            @JsonProperty("messageId") UUID messageId,
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("payloadType") String payloadType,
            @JsonProperty("payload") T payload) {
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.payloadType = payloadType;
        this.payload = payload;
    }

    /**
     * Creates a new MessageEnvelope with auto-generated metadata
     * 
     * @param payload The message payload
     * @param <T>     The type of the payload
     * @return A new MessageEnvelope with generated ID and timestamp
     */
    public static <T> MessageEnvelope<T> create(T payload) {
        return new MessageEnvelope<>(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                payload.getClass().getName(),
                payload);
    }

    /**
     * Creates a new MessageEnvelope with specified correlation ID
     * 
     * @param payload       The message payload
     * @param correlationId Correlation ID for tracing
     * @param <T>           The type of the payload
     * @return A new MessageEnvelope
     */
    public static <T> MessageEnvelope<T> create(T payload, UUID correlationId) {
        return new MessageEnvelope<>(
                UUID.randomUUID(),
                correlationId,
                Instant.now(),
                payload.getClass().getName(),
                payload);
    }
}
