package com.comp5348.store.controller;

import com.comp5348.store.dto.*;
import com.comp5348.store.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkouts")
public class CheckoutController {

    private final CheckoutService checkouts;

    public CheckoutController(CheckoutService checkouts) {
        this.checkouts = checkouts;
    }

    @PostMapping
    public ResponseEntity<CheckoutView> create(
            @RequestAttribute("authenticatedUserId") Long userId,
            @Valid @RequestBody CreateCheckoutRequest req) {
        return ResponseEntity.ok(checkouts.create(userId, req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CheckoutView> get(
            @RequestAttribute("authenticatedUserId") Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(checkouts.get(userId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CheckoutView> update(
            @RequestAttribute("authenticatedUserId") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCheckoutRequest req) {
        return ResponseEntity.ok(checkouts.update(userId, id, req));
    }

    @PostMapping("/{id}/quote")
    public ResponseEntity<CheckoutView> quote(
            @RequestAttribute("authenticatedUserId") Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(checkouts.quote(userId, id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<CheckoutView> confirm(
            @RequestAttribute("authenticatedUserId") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ConfirmCheckoutRequest req) {
        return ResponseEntity.ok(checkouts.confirm(userId, id, req.quoteVersion()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<OrderView> complete(
            @RequestAttribute("authenticatedUserId") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CompleteCheckoutRequest req) {
        return ResponseEntity.ok(checkouts.complete(userId, id, req));
    }
}
