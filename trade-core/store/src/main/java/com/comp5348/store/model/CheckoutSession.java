package com.comp5348.store.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "checkout_session")
public class CheckoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** DRAFT | QUOTED | CONFIRMED | COMPLETED | EXPIRED */
    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(nullable = false)
    private int quoteVersion = 0;

    @Column(precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 18, scale = 2)
    private BigDecimal shippingFee;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 8)
    private String currency = "CNY";

    @Column(length = 512)
    private String shippingAddress;

    private Long orderId;

    @Column(length = 48)
    private String completionKey;

    private Instant quotedAt;
    private Instant confirmedAt;
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getQuoteVersion() { return quoteVersion; }
    public void setQuoteVersion(int quoteVersion) { this.quoteVersion = quoteVersion; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public Long getOrderId() { return orderId; }
    public String getCompletionKey() { return completionKey; }
    public void setCompletionKey(String completionKey) { this.completionKey = completionKey; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Instant getQuotedAt() { return quotedAt; }
    public void setQuotedAt(Instant quotedAt) { this.quotedAt = quotedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
