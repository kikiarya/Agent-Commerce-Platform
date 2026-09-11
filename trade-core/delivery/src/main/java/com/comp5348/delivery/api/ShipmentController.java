package com.comp5348.delivery.api;

import com.comp5348.delivery.domain.Shipment;
import com.comp5348.delivery.service.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ShipmentController {
    private final DeliveryService service;
    public ShipmentController(DeliveryService service) { this.service = service; }

    public record CreateReq(Long orderId, List<Map<String,Object>> items) {}

    @PostMapping("/shipments")
    public ResponseEntity<Map<String,Object>> create(@RequestBody CreateReq req) {
        Shipment s = service.createShipment(req.orderId(), req.items());
        return ResponseEntity.ok(Map.of("shipmentId", s.getId(), "status", s.getStatus().name()));
    }

    @GetMapping("/health")
    public String health() { return "OK"; }
}

