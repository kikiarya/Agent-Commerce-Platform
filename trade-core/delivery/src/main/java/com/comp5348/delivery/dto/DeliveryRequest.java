package com.comp5348.delivery.dto;

import java.util.List;
import java.util.Map;

public record DeliveryRequest(
        Long orderId,
        List<Map<String, Object>> items
) {}
