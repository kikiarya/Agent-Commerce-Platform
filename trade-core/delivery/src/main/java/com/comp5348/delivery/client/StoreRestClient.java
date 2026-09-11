package com.comp5348.delivery.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class StoreRestClient {
    private static final Logger log = LoggerFactory.getLogger(StoreRestClient.class);
    
    private final RestClient restClient;
    private final String storeBaseUrl;
    
    public record OrderInfo(Long userId, BigDecimal totalAmount, String email) {}
    
    public StoreRestClient(
            RestClient restClient,
            @Value("${store.base-url:http://localhost:8080}") String storeBaseUrl) {
        this.restClient = restClient;
        this.storeBaseUrl = storeBaseUrl;
        log.info("StoreRestClient initialized with base URL: {}", storeBaseUrl);
    }
    
    /**
     * Get order status
     */
    public Optional<String> getOrderStatus(Long orderId) {
        try {
            log.info("Calling REST API GetOrderStatus for orderId: {}", orderId);
            
            String url = storeBaseUrl + "/api/internal/orders/" + orderId + "/status";
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("found"))) {
                String status = (String) response.get("status");
                log.info("Found status for order {}: {}", orderId, status);
                return Optional.of(status);
            } else {
                log.warn("No order found: {}", orderId);
                return Optional.empty();
            }
            
        } catch (RestClientException e) {
            log.error("Error getting order status via REST API: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Check if order is paid
     */
    public boolean isPaid(Long orderId) {
        return getOrderStatus(orderId).map("PAID"::equals).orElse(false);
    }
    
    /**
     * Check if order is cancelled
     */
    public boolean isOrderCancelled(Long orderId) {
        return getOrderStatus(orderId).map(status -> "CANCELLED".equalsIgnoreCase(status)).orElse(false);
    }
    
    /**
     * Get order info (userId, totalAmount, email)
     */
    public Optional<OrderInfo> getOrderInfo(Long orderId) {
        try {
            log.info("Calling REST API GetOrderInfo for orderId: {}", orderId);
            
            String url = storeBaseUrl + "/api/internal/orders/" + orderId + "/info";
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("found"))) {
                Long userId = ((Number) response.get("userId")).longValue();
                BigDecimal totalAmount = new BigDecimal((String) response.get("totalAmount"));
                String email = (String) response.get("email");
                
                OrderInfo info = new OrderInfo(userId, totalAmount, email);
                log.info("Found order info for order {}: userId={}, amount={}, email={}", 
                        orderId, info.userId(), info.totalAmount(), info.email());
                return Optional.of(info);
            } else {
                log.warn("No order info found for: {}", orderId);
                return Optional.empty();
            }
            
        } catch (RestClientException e) {
            log.error("Error getting order info via REST API: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Update order status
     */
    public boolean updateOrderStatus(Long orderId, String status) {
        try {
            log.info("Calling REST API UpdateOrderStatus for orderId: {}, status: {}", orderId, status);
            
            String url = storeBaseUrl + "/api/internal/orders/" + orderId + "/status";
            
            Map<String, String> body = new HashMap<>();
            body.put("status", status);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.put()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            
            boolean success = response != null && Boolean.TRUE.equals(response.get("success"));
            log.info("Order {} status update result: {}", orderId, success);
            return success;
            
        } catch (RestClientException e) {
            log.error("Error updating order status via REST API: {}", e.getMessage());
            return false;
        }
    }
}


