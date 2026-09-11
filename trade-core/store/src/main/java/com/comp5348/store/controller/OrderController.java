package com.comp5348.store.controller;

import com.comp5348.store.dto.CreateOrderRequest;
import com.comp5348.store.dto.OrderView;
import com.comp5348.store.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    /**
     * Legacy direct order (debug). Prefer POST /api/checkouts/{id}/complete.
     * X-User-Id is required (no default user 1).
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderView> create(
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value="bankMock", required=false) String bankMock,
            @Valid @RequestBody CreateOrderRequest req) {
        OrderView view = orders.placeSingleItem(userId, req.productId(), req.quantity(), key, bankMock);
        return ResponseEntity.ok(view);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderView> get(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        OrderView v = orders.getForUser(id, userId);
        return v == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(v);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(orders.getAllOrders(userId));
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<OrderView> cancelOrder(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        OrderView v = orders.cancelOrder(id, userId);
        return v == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(v);
    }
}
