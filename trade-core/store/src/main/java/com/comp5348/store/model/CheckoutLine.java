package com.comp5348.store.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "checkout_line")
public class CheckoutLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_id")
    private CheckoutSession checkout;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    public Long getId() { return id; }
    public CheckoutSession getCheckout() { return checkout; }
    public void setCheckout(CheckoutSession checkout) { this.checkout = checkout; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
