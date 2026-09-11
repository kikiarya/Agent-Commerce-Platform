package com.comp5348.delivery.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "shipment", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
public class Shipment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="order_id", nullable=false, unique=true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=32)
    private ShipmentStatus status = ShipmentStatus.REQUESTED;

    @Column(nullable=false)
    private Instant updatedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private ShipmentStatus pendingStatus;
    public ShipmentStatus getPendingStatus() { return pendingStatus; }
    public void setPendingStatus(ShipmentStatus value) { pendingStatus = value; }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
