package com.comp5348.bank.dto;

import java.math.BigDecimal;

public record RefundRequest(
        Long orderId,
        BigDecimal amount,
        String idempotencyKey,
        Long userId
) {}
