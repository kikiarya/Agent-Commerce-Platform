package com.comp5348.store.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;



@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // Simplified: fixed demo user

    @Column(nullable = false)
    private String status; // CREATED, PAID, FULFILLED, FAILED, CANCELLING, CANCELLED

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt;

    private String paymentMock;

    /** Links to CheckoutSession when created via complete checkout */
    private Long checkoutId;

    /** Same as Bank / PaymentAttempt.paymentAttemptId (usually = idempotencyKey) */
    @Column(length = 48)
    private String paymentAttemptId;

    /** null | PENDING | REFUNDED | UNVERIFIED | FAILED */
    @Column(length = 16)
    private String refundStatus;

    @Version
    private Long version;

    // --- Lifecycle callback: set createdAt before insert ---
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
