package com.comp5348.store.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_attempt", uniqueConstraints = @UniqueConstraint(columnNames = "payment_attempt_id"))
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_attempt_id", nullable = false, length = 48)
    private String paymentAttemptId;

    @Column(nullable = false)
    private Long orderId;

    /** UNKNOWN | SUCCESS | FAILED */
    @Column(nullable = false, length = 16)
    private String status = "UNKNOWN";

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPaymentAttemptId() { return paymentAttemptId; }
    public void setPaymentAttemptId(String paymentAttemptId) { this.paymentAttemptId = paymentAttemptId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
