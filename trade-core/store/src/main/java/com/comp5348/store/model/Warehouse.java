package com.comp5348.store.model;

import jakarta.persistence.*;

@Entity
@Table(name = "warehouse")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String location;

    // --- JPA required no-arg constructor ---
    public Warehouse() {}

    // --- Optional convenience constructor ---
    public Warehouse(String name, String location) {
        this.name = name;
        this.location = location;
    }

    // --- getters / setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {  // Generally not manually set, kept for compatibility
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
