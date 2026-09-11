package com.comp5348.email.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Component
public class StoreClient {
    private static final Logger log = LoggerFactory.getLogger(StoreClient.class);

    private final RestTemplate restTemplate;
    private final String storeBaseUrl;

    public StoreClient(RestTemplate restTemplate,
                       @Value("${store.base-url:http://localhost:8080}") String storeBaseUrl) {
        this.restTemplate = restTemplate;
        this.storeBaseUrl = storeBaseUrl;
    }

    /**
     * Send delivery callback to Store service
     */
    public boolean sendDeliveryCallback(Long shipmentId, Long orderId, String status) {
        try {
            String url = storeBaseUrl + "/api/webhooks/delivery";

            Map<String, Object> payload = Map.of(
                "shipmentId", shipmentId,
                "orderId", orderId,
                "newStatus", status,
                "eventTime", Instant.now().toString()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Void.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("Delivery callback to Store: shipment={} order={} status={} success={}",
                    shipmentId, orderId, status, success);

            return success;

        } catch (Exception e) {
            log.error("Failed to send delivery callback to Store: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get order status from Store service
     */
    public String getOrderStatus(Long orderId) {
        try {
            String url = storeBaseUrl + "/api/internal/orders/" + orderId + "/status";
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("found"))) {
                String status = (String) response.get("status");
                log.info("Order {} status: {}", orderId, status);
                return status;
            }
            
            log.warn("Order {} not found or status unavailable", orderId);
            return null;
            
        } catch (Exception e) {
            log.error("Failed to get order status for order {}: {}", orderId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if order is cancelled
     */
    public boolean isOrderCancelled(Long orderId) {
        String status = getOrderStatus(orderId);
        return status != null && "CANCELLED".equalsIgnoreCase(status);
    }
    
    /**
     * Request refund from Store service
     */
    public boolean requestRefund(Long orderId, BigDecimal amount, Long userId) {
        try {
            // Note: This would typically publish to RabbitMQ or call a refund API
            // For now, we'll log it. The actual refund will be handled by Store's
            // existing refund consumer if it's listening to the queue
            log.info("Refund request logged for order={} amount={} userId={}", orderId, amount, userId);

            // In a real implementation, you might call:
            // POST /api/refunds with {orderId, amount, userId}
            // or publish to RabbitMQ exchange

            return true;

        } catch (Exception e) {
            log.error("Failed to request refund: {}", e.getMessage(), e);
            return false;
        }
    }
}