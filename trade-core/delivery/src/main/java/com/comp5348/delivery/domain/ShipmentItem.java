package com.comp5348.delivery.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "shipment_item")
public class ShipmentItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false) @JoinColumn(name="shipment_id")
    private Shipment shipment;

    @Column(nullable=false) private Long warehouseId;
    @Column(nullable=false) private Long productId;
    @Column(nullable=false) private Integer qty;

    public Long getId() { return id; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment shipment) { this.shipment = shipment; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
}
