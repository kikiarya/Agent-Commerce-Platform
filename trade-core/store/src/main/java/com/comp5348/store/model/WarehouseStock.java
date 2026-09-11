package com.comp5348.store.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "warehouse_stock",
        uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "product_id"})
)
public class WarehouseStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Version
    private Long version; // Optimistic lock to prevent overselling

    // --- JPA required no-arg constructor ---
    public WarehouseStock() {}

    // --- Optional convenience constructor ---
    public WarehouseStock(Warehouse warehouse, Product product, Integer quantity) {
        this.warehouse = warehouse;
        this.product = product;
        this.quantity = quantity;
    }

    // --- getters / setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {  // Generally not manually set, kept for compatibility
        this.id = id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
