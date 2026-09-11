package com.comp5348.delivery.service;

import com.comp5348.delivery.domain.*;
import com.comp5348.delivery.repo.*;
import jakarta.transaction.Transactional;
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.comp5348.delivery.client.EmailGrpcClient;
import com.comp5348.delivery.client.StoreRestClient;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final ShipmentRepository shipments;
    private final ShipmentItemRepository items;
    private final ShipmentEventRepository events;
    private final RestClient http;
    private final String callbackUrl;
    private final double lossRate;
    private final Random random = new Random();
    private final EmailGrpcClient emailGrpcClient;
    private final StoreRestClient storeRestClient;

    public DeliveryService(ShipmentRepository shipments,
                          ShipmentItemRepository items,
                          ShipmentEventRepository events,
                          RestClient http,
                          @Value("${delivery.callback-url}") String callbackUrl,
                          @Value("${delivery.loss-rate:0.05}") double lossRate,
                          @Autowired(required = false) EmailGrpcClient emailGrpcClient,
                          StoreRestClient storeRestClient) {
        this.shipments = shipments; this.items = items; this.events = events;
        this.http = http; this.callbackUrl = callbackUrl; this.lossRate = lossRate;
        this.emailGrpcClient = emailGrpcClient;
        this.storeRestClient = storeRestClient;

        log.info("DeliveryService configured with EmailGrpcClient and StoreRestClient");
    }

    @Transactional
    public Shipment createShipment(Long orderId, List<Map<String,Object>> reqItems) {
        var existing = shipments.findByOrderId(orderId);
        if (existing.isPresent()) return existing.get();
        String orderStatus = storeRestClient.getOrderStatus(orderId)
                .orElseThrow(() -> new IllegalStateException("Order status unavailable; retry delivery"));
        if ("PAYMENT_PENDING".equals(orderStatus) || "CREATED".equals(orderStatus))
            throw new IllegalStateException("Payment still pending; retry delivery");
        if (!"PAID".equals(orderStatus)) {
            log.warn("Rejecting shipment creation. Order {} is not PAID. Discarding delivery request.", orderId);
            // Return null to indicate rejection without triggering retry
            // This allows RabbitMQ to acknowledge and remove the message
            return null;
        }
        Shipment s = new Shipment();
        s.setOrderId(orderId);
        s.setStatus(ShipmentStatus.REQUESTED);
        shipments.save(s);

        for (Map<String,Object> it : reqItems) {
            ShipmentItem si = new ShipmentItem();
            si.setShipment(s);
            si.setWarehouseId(Long.valueOf(it.get("warehouseId").toString()));
            si.setProductId(Long.valueOf(it.get("productId").toString()));
            si.setQty(Integer.valueOf(it.get("qty").toString()));
            items.save(si);
        }
        recordEvent(s, "REQUESTED");
        log.info("Created shipment {} for order {}", s.getId(), orderId);
        return s;
    }

    /** Persist the intended transition before calling Email/Store; retry on next tick. */
    @Scheduled(fixedDelayString = "${delivery.step-seconds:5}000")
    public void advanceAll() {
        var inProgress = shipments.findByStatusIn(List.of(ShipmentStatus.REQUESTED,
                ShipmentStatus.PICKUP, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY));
        for (Shipment shipment : inProgress) {
            try {
                var status = storeRestClient.getOrderStatus(shipment.getOrderId());
                if (status.isEmpty()) continue;
                if ("CANCELLED".equals(status.get()) && shipment.getPendingStatus() != ShipmentStatus.LOST) continue;
                var info = storeRestClient.getOrderInfo(shipment.getOrderId());
                if (info.isEmpty()) continue;
                ShipmentStatus next = shipment.getPendingStatus();
                if (next == null) {
                    next = random.nextDouble() < lossRate ? ShipmentStatus.LOST : shipment.getStatus().next();
                    shipment.setPendingStatus(next);
                    shipments.saveAndFlush(shipment);
                }
                boolean accepted = next == ShipmentStatus.LOST
                        ? emailGrpcClient.sendDeliveryLossNotification(shipment.getOrderId(), shipment.getId(),
                            info.get().userId(), info.get().email(), info.get().totalAmount().toString())
                        : emailGrpcClient.sendShipmentNotification(shipment.getOrderId(), shipment.getId(), next.name(), info.get().email());
                if (!accepted) continue;
                shipment.setStatus(next); shipment.setPendingStatus(null); shipment.setUpdatedAt(Instant.now());
                shipments.save(shipment);
                recordEvent(shipment, next.name());
            } catch (Exception e) { log.warn("Shipment {} transition remains pending", shipment.getId(), e); }
        }
    }

    private void recordEvent(Shipment s, String type) {
        ShipmentEvent ev = new ShipmentEvent();
        ev.setShipment(s); ev.setEventType(type);
        events.save(ev);
    }
}
