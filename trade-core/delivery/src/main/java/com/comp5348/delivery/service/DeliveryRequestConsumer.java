package com.comp5348.delivery.service;

import com.comp5348.delivery.config.RabbitMQConfig;
import com.comp5348.delivery.dto.DeliveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DeliveryRequestConsumer {
    private static final Logger log = LoggerFactory.getLogger(DeliveryRequestConsumer.class);
    
    private final DeliveryService deliveryService;

    public DeliveryRequestConsumer(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @RabbitListener(queues = RabbitMQConfig.DELIVERY_REQUEST_QUEUE)
    public void processDeliveryRequest(DeliveryRequest deliveryRequest) {
        log.info("Processing delivery request for orderId: {}, {} items", 
            deliveryRequest.orderId(), deliveryRequest.items().size());
        
        try {
            // Create shipment using the existing DeliveryService method
            var shipment = deliveryService.createShipment(
                deliveryRequest.orderId(), 
                deliveryRequest.items()
            );
            
            if (shipment != null) {
                log.info("Successfully created shipment {} for order {} with {} items", 
                    shipment.getId(), deliveryRequest.orderId(), deliveryRequest.items().size());
            } else {
                log.info("Delivery request for order {} was rejected (order not in PAID status). Message acknowledged.", 
                    deliveryRequest.orderId());
            }
            
        } catch (Exception e) {
            // Technical errors (e.g., DB connection) - should retry
            log.error("Technical error processing delivery request for orderId: {}, error: {}", 
                deliveryRequest.orderId(), e.getMessage(), e);
            // Re-throw to trigger retry/DLQ
            throw e;
        }
    }
}
