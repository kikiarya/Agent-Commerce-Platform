package com.comp5348.store.model;

import jakarta.persistence.*;
import java.time.Instant;


@Entity
@Table(
        name = "delivery_event",
        uniqueConstraints = @UniqueConstraint(name="uk_shipment_status", columnNames={"shipment_id","event_type"})
)
public class DeliveryEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="shipment_id", nullable=false)
    private Long shipmentId;

    @Column(name="order_id", nullable=false)
    private Long orderId;

    @Column(name="event_type", nullable=false, length=32) // REQUESTED/PICKUP/IN_TRANSIT/OUT_FOR_DELIVERY/DELIVERED/LOST
    private String eventType;

    @Column(name="event_time", nullable=false)
    private Instant eventTime = Instant.now();

    public DeliveryEvent() {}

    public DeliveryEvent(Long shipmentId, Long orderId, String eventType, Instant eventTime) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.eventType = eventType;
        if (eventTime != null) this.eventTime = eventTime;
    }

    public Long getId() { return id; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }
}

