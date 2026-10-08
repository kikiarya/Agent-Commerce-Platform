package com.comp5348.store.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_attribute", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attribute_name", length = 80)
    @Column(name = "attribute_value", length = 200)
    private java.util.Map<String, String> attributes = new java.util.LinkedHashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_purpose", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "purpose", length = 80)
    private java.util.Set<String> purposes = new java.util.LinkedHashSet<>();

    public java.util.Map<String,String> getAttributes() { return attributes; }
    public void setAttributes(java.util.Map<String,String> value) { attributes = value == null ? new java.util.LinkedHashMap<>() : value; }
    public java.util.Set<String> getPurposes() { return purposes; }
    public void setPurposes(java.util.Set<String> value) { purposes = value == null ? new java.util.LinkedHashSet<>() : value; }

    // --- JPA required no-arg constructor ---
    public Product() {}

    // --- Optional convenience constructor ---
    public Product(String sku, String name, BigDecimal price) {
        this.sku = sku;
        this.name = name;
        this.price = price;
    }

    // --- getters / setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {  // Generally not manually set, kept for compatibility
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
