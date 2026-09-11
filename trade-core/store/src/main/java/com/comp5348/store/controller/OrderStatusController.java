package com.comp5348.store.controller;

import com.comp5348.store.model.Order;
import com.comp5348.store.model.User;
import com.comp5348.store.repository.OrderRepository;
import com.comp5348.store.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API endpoint - as backup for gRPC
 * Provides order status query functionality
 */
@RestController
@RequestMapping("/api/internal/orders")
public class OrderStatusController {
    private static final Logger log = LoggerFactory.getLogger(OrderStatusController.class);
    
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    
    public OrderStatusController(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }
    
    /**
     * Get order status
     * GET /api/internal/orders/{orderId}/status
     */
    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, Object>> getOrderStatus(@PathVariable Long orderId) {
        log.info("REST API: Getting status for order {}", orderId);
        
        Order order = orderRepository.findById(orderId).orElse(null);
        
        Map<String, Object> response = new HashMap<>();
        if (order == null) {
            response.put("found", false);
            return ResponseEntity.ok(response);
        }
        
        response.put("found", true);
        response.put("status", order.getStatus());
        log.info("REST API: Order {} status is {}", orderId, order.getStatus());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get order details (including user email)
     * GET /api/internal/orders/{orderId}/info
     */
    @GetMapping("/{orderId}/info")
    public ResponseEntity<Map<String, Object>> getOrderInfo(@PathVariable Long orderId) {
        log.info("REST API: Getting info for order {}", orderId);
        
        Order order = orderRepository.findById(orderId).orElse(null);
        
        Map<String, Object> response = new HashMap<>();
        if (order == null) {
            response.put("found", false);
            return ResponseEntity.ok(response);
        }
        
        User user = userRepository.findById(order.getUserId()).orElse(null);
        if (user == null) {
            response.put("found", false);
            return ResponseEntity.ok(response);
        }
        
        response.put("found", true);
        response.put("userId", order.getUserId());
        response.put("totalAmount", order.getTotalAmount().toString());
        response.put("email", user.getEmail());
        
        log.info("REST API: Order {} info - userId={}, email={}", orderId, order.getUserId(), user.getEmail());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update order status
     * PUT /api/internal/orders/{orderId}/status
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Map<String, Object>> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body) {
        
        String newStatus = body.get("status");
        log.info("REST API: Updating order {} status to {}", orderId, newStatus);
        
        Order order = orderRepository.findById(orderId).orElse(null);
        
        Map<String, Object> response = new HashMap<>();
        if (order == null) {
            response.put("success", false);
            return ResponseEntity.ok(response);
        }
        
        order.setStatus(newStatus);
        orderRepository.save(order);
        
        response.put("success", true);
        log.info("REST API: Successfully updated order {} status to {}", orderId, newStatus);
        return ResponseEntity.ok(response);
    }
}


