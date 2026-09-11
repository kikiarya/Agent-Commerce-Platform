// dto/DeliveryWebhookPayload.java
package com.comp5348.store.dto;

import java.time.Instant;

public record DeliveryWebhookPayload(
        Long shipmentId,
        Long orderId,
        String newStatus,
        Instant eventTime
) {}
