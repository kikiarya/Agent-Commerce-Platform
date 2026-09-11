package com.comp5348.store.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(optional = false)
    private Product product;

    @Column(nullable = false)
    private Integer qty;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal priceAtOrder;

    @Column(nullable = true)
    private Long warehouseStockId; // Track which warehouse stock was reserved

    // --- JPA required no-arg constructor ---
    public OrderItem() {}

    // --- Optional convenience constructor ---
    public OrderItem(Order order, Product product, Integer qty, BigDecimal priceAtOrder) {
        this.order = order;
        this.product = product;
        this.qty = qty;
        this.priceAtOrder = priceAtOrder;
    }

    // --- getters / setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {  // Generally not manually set, kept for compatibility
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public BigDecimal getPriceAtOrder() {
        return priceAtOrder;
    }

    public void setPriceAtOrder(BigDecimal priceAtOrder) {
        this.priceAtOrder = priceAtOrder;
    }

    public Long getWarehouseStockId() {
        return warehouseStockId;
    }

    public void setWarehouseStockId(Long warehouseStockId) {
        this.warehouseStockId = warehouseStockId;
    }
}
