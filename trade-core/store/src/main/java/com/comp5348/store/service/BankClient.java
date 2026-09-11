// service/BankClient.java
package com.comp5348.store.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class BankClient {
    private static final Logger log = LoggerFactory.getLogger(BankClient.class);
    
    private final RestClient http;
    private final String base;

    public BankClient(RestClient http, @Value("${store.bank-base}") String base) {
        this.http = http; this.base = base;
    }

    /**
     * Payment - requires userId
     */
    public enum PaymentOutcome { SUCCESS, FAILED, UNKNOWN }

    public PaymentOutcome payOutcome(Long userId, Long orderId, BigDecimal amount, String idemKey, String mock) {
        Map<String,Object> body = Map.of(
            "userId", userId,
            "orderId", orderId, 
            "amount", amount, 
            "idempotencyKey", idemKey
        );
        String url = base + "/api/payments" + (mock != null ? "?mock=" + mock : "");
        
        try {
            var resp = http.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .toEntity(Map.class);
            Object status = resp.getBody() != null ? resp.getBody().get("status") : null;
            return mapStatus(status);
        } catch (Exception e) {
            log.warn("Payment outcome unknown; will query then replay same key", e);
            return PaymentOutcome.UNKNOWN;
        }
    }

    /**
     * Query Bank by paymentAttemptId / idempotency key. 404 → UNKNOWN (not yet recorded).
     */
    public PaymentOutcome queryOutcome(String paymentAttemptId) {
        try {
            var resp = http.get()
                    .uri(base + "/api/payments/by-key/" + paymentAttemptId)
                    .retrieve()
                    .toEntity(Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return PaymentOutcome.UNKNOWN;
            }
            return mapStatus(resp.getBody().get("status"));
        } catch (Exception e) {
            log.warn("Payment query unknown for key {}", paymentAttemptId, e);
            return PaymentOutcome.UNKNOWN;
        }
    }

    private static PaymentOutcome mapStatus(Object status) {
        if ("SUCCESS".equals(status) || "REFUNDED".equals(status)) return PaymentOutcome.SUCCESS;
        if ("FAILED".equals(status)) return PaymentOutcome.FAILED;
        return PaymentOutcome.UNKNOWN;
    }

    /**
     * Refund - requires userId
     */
    public void refund(Long userId, Long orderId, BigDecimal amount, String idemKey) {
        Map<String,Object> body = Map.of(
            "userId", userId,
            "orderId", orderId, 
            "amount", amount, 
            "idempotencyKey", idemKey
        );
        
        try {
            http.post().uri(base + "/api/refunds").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.error("Refund error: {}", e.getMessage(), e);
            throw new RuntimeException("Refund failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create bank account
     */
    public boolean createBankAccount(Long userId, String holderName, BigDecimal initialBalance) {
        Map<String,Object> body = Map.of(
            "holderName", holderName,
            "accountType", "PERSONAL",
            "userId", userId,
            "initialBalance", initialBalance
        );
        
        try {
            var resp = http.post()
                    .uri(base + "/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);
            
            log.info("Created bank account for user {}: {}", userId, resp.getBody());
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to create bank account for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Query user's bank account
     */
    public Map<String, Object> getBankAccount(Long userId) {
        try {
            var resp = http.get()
                    .uri(base + "/api/accounts/user/" + userId)
                    .retrieve()
                    .toEntity(Map.class);
            
            if (resp.getStatusCode().is2xxSuccessful()) {
                return resp.getBody();
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get bank account for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
