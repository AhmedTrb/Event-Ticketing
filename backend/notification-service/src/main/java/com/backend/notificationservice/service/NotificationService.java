package com.backend.notificationservice.service;

import com.backend.notificationservice.domain.Notification;
import com.backend.notificationservice.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processNotification(Map<String, Object> messageData) {
        try {
            String userIdStr = (String) messageData.get("userId");
            String message = (String) messageData.get("message");
            String type = (String) messageData.get("type");

            if (userIdStr == null || message == null) {
                log.warn("Invalid notification data: {}", messageData);
                return;
            }

            UUID userId = UUID.fromString(userIdStr);

            // 1. Save to DB
            Notification notification = Notification.builder()
                    .userId(userId)
                    .message(message)
                    .type(type)
                    .read(false)
                    .build();

            notificationRepository.save(notification);
            log.info("Saved notification for user {}", userId);

            // 2. Broadcast via WebSocket
            // Destination: /user/{userId}/queue/notifications or
            // /topic/notifications/{userId}
            // Using SimpMessagingTemplate.convertAndSendToUser sends to /user/{userId}/...
            // But here we might just use a topic: /topic/user/{userId}/notifications
            // Ideally we use User destination for security, but simpler here is topic.

            String destination = "/topic/user/" + userId + "/notifications";
            messagingTemplate.convertAndSend(destination, notification);

            log.info("Broadcasted notification to {}", destination);

        } catch (Exception e) {
            log.error("Error processing notification", e);
        }
    }
}
