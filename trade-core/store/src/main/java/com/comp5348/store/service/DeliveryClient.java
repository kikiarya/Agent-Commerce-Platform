// service/DeliveryClient.java
package com.comp5348.store.service;

import com.comp5348.store.dto.DeliveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DeliveryClient {
    private static final Logger log = LoggerFactory.getLogger(DeliveryClient.class);
    
    private final MessagePublisher messagePublisher;

    public DeliveryClient(MessagePublisher messagePublisher) {
        this.messagePublisher = messagePublisher;
    }

    /**
     * Create single warehouse shipment - send asynchronously via message queue
     */
    public void createShipment(Long orderId, Long warehouseId, Long productId, int qty) {
        Map<String,Object> item = Map.of("warehouseId", warehouseId, "productId", productId, "qty", qty);
        DeliveryRequest request = new DeliveryRequest(orderId, List.of(item));
        messagePublisher.publishDeliveryRequest(request);
        log.info("Published delivery request for orderId: {}, warehouseId: {}, productId: {}, qty: {}", 
            orderId, warehouseId, productId, qty);
    }

    /**
     * Create multi-warehouse shipment - send asynchronously via message queue
     * 
     * @param orderId Order ID
     * @param allocations Warehouse allocation list
     * @param productId Product ID
     */
    public void createMultiWarehouseShipment(Long orderId, 
                                           List<com.comp5348.store.dto.WarehouseAllocation> allocations,
                                           Long productId) {
        List<Map<String,Object>> items = allocations.stream()
            .map(alloc -> Map.of(
                "warehouseId", (Object) alloc.warehouseId(),
                "productId", (Object) productId,
                "qty", (Object) alloc.quantity()
            ))
            .collect(Collectors.toList());
        
        DeliveryRequest request = new DeliveryRequest(orderId, items);
        messagePublisher.publishDeliveryRequest(request);
        log.info("Published multi-warehouse delivery request for orderId: {}, productId: {}, {} warehouses", 
            orderId, productId, items.size());
    }
}