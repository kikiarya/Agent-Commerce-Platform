package com.comp5348.delivery.dto;

import java.math.BigDecimal;

public record RefundRequest(
        Long orderId,
        BigDecimal amount,
        String idempotencyKey,
        Long userId
) {}
