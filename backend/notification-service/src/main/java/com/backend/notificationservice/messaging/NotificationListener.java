package com.backend.notificationservice.messaging;

import com.backend.notificationservice.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * Called by RedisMessageListenerContainer when a message is received on the
     * topic.
     * The message is a simple JSON string.
     */
    public void onMessage(String message, String channel) {
        log.info("Received Redis message on channel {}: {}", channel, message);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(message, Map.class);
            notificationService.processNotification(messageData);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse notification message", e);
        }
    }
}
