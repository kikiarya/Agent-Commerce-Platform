package com.comp5348.store.service;

import com.comp5348.common.util.EmailMessageTemplate;
import com.comp5348.store.dto.DeliveryWebhookPayload;
import com.comp5348.store.dto.RefundRequest;
import com.comp5348.store.model.DeliveryEvent;
import com.comp5348.store.model.Order;
import com.comp5348.store.model.User;
import com.comp5348.store.repository.DeliveryEventRepository;
import com.comp5348.store.repository.OrderRepository;
import com.comp5348.store.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class DeliveryWebhookService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWebhookService.class);

    private final DeliveryEventRepository events;
    private final OrderRepository orders;
    private final MessagePublisher messagePublisher;
    private final UserRepository userRepository;
    private final WebSocketNotificationService webSocketNotificationService;

    public DeliveryWebhookService(DeliveryEventRepository events, OrderRepository orders,
                                  MessagePublisher messagePublisher, UserRepository userRepository,
                                  WebSocketNotificationService webSocketNotificationService) {
        this.events = events;
        this.orders = orders;
        this.messagePublisher = messagePublisher;
        this.userRepository = userRepository;
        this.webSocketNotificationService = webSocketNotificationService;
    }

    @Transactional
    public void handle(DeliveryWebhookPayload p) {
        String status = (p.newStatus() == null ? "" : p.newStatus()).toUpperCase(Locale.ROOT);

        // Idempotency: same (shipmentId, status) already processed, return directly
        if (events.findByShipmentIdAndEventType(p.shipmentId(), status).isPresent()) {
            log.info("Duplicate delivery event ignored: shipment={} status={}", p.shipmentId(), status);
            return;
        }

        // Get order info (check order status before saving event)
        Order o = orders.findLockedById(p.orderId()).orElse(null);
        if (o == null) {
            log.warn("Order not found for delivery callback: orderId={}", p.orderId());
            return;
        }

        // Skip notifications for cancelled orders (check early to avoid saving events)
        if ("CANCELLED".equalsIgnoreCase(o.getStatus())) {
            log.info("Order {} is CANCELLED, skipping delivery callback and notification for status {}", o.getId(), status);
            // Still save the event for tracking, but don't process notification
            DeliveryEvent ev = new DeliveryEvent(p.shipmentId(), p.orderId(), status, p.eventTime());
            events.save(ev);
            return;
        }

        // Save event
        DeliveryEvent ev = new DeliveryEvent(p.shipmentId(), p.orderId(), status, p.eventTime());
        events.save(ev);

        // Get user email
        String userEmail = getUserEmail(o.getUserId());

        // Send email notification based on delivery status
        // Note: Re-check order status before sending each notification to ensure order wasn't cancelled
        switch (status) {
            case "PICKUP" -> {
                // Double check order is still not cancelled
                o = orders.findById(o.getId()).orElse(o);
                if ("CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    log.info("Order {} was cancelled, skipping PICKUP notification", o.getId());
                    return;
                }
                // Update order status to DELIVERING when pickup starts
                if (!"DELIVERING".equalsIgnoreCase(o.getStatus()) && !"FULFILLED".equalsIgnoreCase(o.getStatus())) {
                    o.setStatus("DELIVERING");
                    orders.save(o);
                    log.info("Order {} -> DELIVERING (PICKUP)", o.getId());
                }
                // Requirement: "when DeliveryCo has picked up the goods (and they're now in their depot)"
                var emailContent = EmailMessageTemplate.getShipmentStatusMessage("PICKUP");
                sendEmailNotification(o.getId(), "PICKUP", userEmail,
                        emailContent.subject(),
                        emailContent.body());
                log.info("Order {} - PICKUP notification sent", o.getId());
            }
            case "IN_TRANSIT" -> {
                // Double check order is still not cancelled
                o = orders.findById(o.getId()).orElse(o);
                if ("CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    log.info("Order {} was cancelled, skipping IN_TRANSIT notification", o.getId());
                    return;
                }
                // Update order status to DELIVERING when shipment is in transit
                if (!"DELIVERING".equalsIgnoreCase(o.getStatus()) && !"FULFILLED".equalsIgnoreCase(o.getStatus())) {
                    o.setStatus("DELIVERING");
                    orders.save(o);
                    log.info("Order {} -> DELIVERING (IN_TRANSIT)", o.getId());
                }
                // Requirement: "when they're on the delivery truck"
                var emailContent = EmailMessageTemplate.getShipmentStatusMessage("IN_TRANSIT");
                sendEmailNotification(o.getId(), "IN_TRANSIT", userEmail,
                        emailContent.subject(),
                        emailContent.body());
                log.info("Order {} - IN_TRANSIT notification sent", o.getId());
            }
            case "OUT_FOR_DELIVERY" -> {
                // Double check order is still not cancelled
                o = orders.findById(o.getId()).orElse(o);
                if ("CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    log.info("Order {} was cancelled, skipping OUT_FOR_DELIVERY notification", o.getId());
                    return;
                }
                // Requirement: "when they're on the delivery truck"
                // Update order status to DELIVERING
                if (!"DELIVERING".equalsIgnoreCase(o.getStatus()) && !"FULFILLED".equalsIgnoreCase(o.getStatus())) {
                    o.setStatus("DELIVERING");
                    orders.save(o);
                    log.info("Order {} -> DELIVERING", o.getId());
                }
                var emailContent = EmailMessageTemplate.getShipmentStatusMessage("OUT_FOR_DELIVERY");
                sendEmailNotification(o.getId(), "OUT_FOR_DELIVERY", userEmail,
                        emailContent.subject(),
                        emailContent.body());
                log.info("Order {} - OUT_FOR_DELIVERY notification sent", o.getId());
            }
            case "DELIVERED" -> {
                // Double check order is still not cancelled
                o = orders.findById(o.getId()).orElse(o);
                if ("CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    log.info("Order {} was cancelled, skipping DELIVERED notification", o.getId());
                    return;
                }
                // Requirement: "when DeliveryCo claims that the delivery is complete"
                if (!"FULFILLED".equalsIgnoreCase(o.getStatus())) {
                    o.setStatus("FULFILLED");
                    orders.save(o);
                    log.info("Order {} -> FULFILLED", o.getId());
                }
                var emailContent = EmailMessageTemplate.getShipmentStatusMessage("DELIVERED");
                sendEmailNotification(o.getId(), "DELIVERED", userEmail,
                        emailContent.subject(),
                        emailContent.body());
                log.info("Order {} -> FULFILLED, notification sent", o.getId());
            }
            case "LOST" -> {
                if (!"CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    o.setStatus("CANCELLED");
                    orders.save(o);
                    log.info("Order {} -> CANCELLED (due to loss)", o.getId());
                }
                
                // Send LOST email notification
                var emailContent = EmailMessageTemplate.getShipmentStatusMessage("LOST");
                sendEmailNotification(o.getId(), "LOST", userEmail,
                        emailContent.subject(),
                        emailContent.body());
                
                // Trigger refund for lost package
                if (o.getTotalAmount() != null && o.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    String refundIdemKey = "refund-" + o.getId();
                    RefundRequest refundRequest = new RefundRequest(
                            o.getId(),
                            o.getTotalAmount(),
                            refundIdemKey,
                            o.getUserId()
                    );
                    messagePublisher.publishRefundRequest(refundRequest);
                    log.info("Published refund request for lost order {}: amount={}", o.getId(), o.getTotalAmount());
                }
                
                log.info("Order {} -> LOST, notification and refund request sent", o.getId());
            }
            case "REQUESTED" -> {
                // Delivery request received (optional notification)
                log.info("Order {} - REQUESTED status received", o.getId());
            }
            default -> {
                log.info("Order {} - Status {} received (no notification configured)", o.getId(), status);
            }
        }
    }

    /**
     * Send email notification (via RabbitMQ)
     * Note: For delivery status notifications (PICKUP, IN_TRANSIT, etc.), Email service already sent emails during gRPC call,
     * so here we only send WebSocket notifications and do not duplicate email notifications to RabbitMQ
     */
    private void sendEmailNotification(Long orderId, String type, String toEmail, String subject, String body) {
        // Final check: verify order is not cancelled before sending notification
        try {
            var orderOpt = orders.findById(orderId);
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
                    log.info("Order {} is CANCELLED, skipping WebSocket notification for type {}", orderId, type);
                    return;
                }
                Long userId = order.getUserId();
                // Send WebSocket notification directly, not via MessagePublisher (avoid duplicate emails)
                webSocketNotificationService.sendNotificationToUser(userId, type, subject, body, orderId);
                log.info("WebSocket notification sent for orderId: {}, type: {} (email already sent by Email service)", orderId, type);
            }
        } catch (Exception e) {
            log.warn("Failed to send WebSocket notification for orderId: {}, error: {}", orderId, e.getMessage());
        }
    }

    /**
     * Get user email
     */
    private String getUserEmail(Long userId) {
        return userRepository.findById(userId)
                .map(User::getEmail)
                .orElse("customer@example.com");
    }

}
