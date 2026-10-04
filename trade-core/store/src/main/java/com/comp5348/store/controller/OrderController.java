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
    private final com.comp5348.store.repository.DeliveryEventRepository deliveryEvents;
    public OrderController(OrderService orders, com.comp5348.store.repository.DeliveryEventRepository deliveryEvents) {
        this.orders = orders;
        this.deliveryEvents = deliveryEvents;
    }
    public record DeliveryStep(Long shipmentId, String status, java.time.Instant occurredAt) {}

    @GetMapping("/orders/{id}/delivery-events")
    public ResponseEntity<?> deliveryEvents(
            @RequestAttribute("authenticatedUserId") Long userId, @PathVariable Long id) {
        // Check ownership before reading or revealing any delivery information.
        if (orders.getForUser(id, userId) == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(deliveryEvents.findByOrderIdOrderByEventTimeAscIdAsc(id).stream()
                .map(event -> new DeliveryStep(event.getShipmentId(), event.getEventType(), event.getEventTime()))
                .toList());
    }

    /**
     * Legacy direct order (debug). Prefer POST /api/checkouts/{id}/complete.
     * Identity comes from the authenticated Store JWT (no default user 1).
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderView> create(
            @RequestHeader("Idempotency-Key") String key,
            @RequestAttribute("authenticatedUserId") Long userId,
            @RequestParam(value="bankMock", required=false) String bankMock,
            @Valid @RequestBody CreateOrderRequest req) {
        OrderView view = orders.placeSingleItem(userId, req.productId(), req.quantity(), key, bankMock);
        return ResponseEntity.ok(view);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderView> get(
            @RequestAttribute("authenticatedUserId") Long userId,
            @PathVariable Long id) {
        OrderView v = orders.getForUser(id, userId);
        return v == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(v);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders(@RequestAttribute("authenticatedUserId") Long userId) {
        return ResponseEntity.ok(orders.getAllOrders(userId));
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<OrderView> cancelOrder(
            @RequestAttribute("authenticatedUserId") Long userId,
            @PathVariable Long id) {
        OrderView v = orders.cancelOrder(id, userId);
        return v == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(v);
    }
}
