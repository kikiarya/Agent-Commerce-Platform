package com.comp5348.store.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketNotificationService {
    private static final Logger log = LoggerFactory.getLogger(WebSocketNotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Send notification to a specific user
     */
    public void sendNotificationToUser(Long userId, String status, String subject, String body, Long orderId) {
        try {
            NotificationMessage message = new NotificationMessage(orderId, status, subject, body);
            messagingTemplate.convertAndSend("/topic/notifications/" + userId, message);
            log.info("WebSocket notification sent to user {}: status={}, orderId={}", userId, status, orderId);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Notification message DTO
     */
    public record NotificationMessage(Long orderId, String status, String subject, String body) {}
}
