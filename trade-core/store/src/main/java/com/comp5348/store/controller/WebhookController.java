package com.comp5348.store.controller;

import com.comp5348.store.dto.DeliveryWebhookPayload;
import com.comp5348.store.service.DeliveryWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final DeliveryWebhookService service;
    public WebhookController(DeliveryWebhookService service) { this.service = service; }

    @PostMapping("/delivery")
    public ResponseEntity<Void> onDelivery(@RequestBody DeliveryWebhookPayload payload) {
        service.handle(payload);
        return ResponseEntity.ok().build();
    }
}
