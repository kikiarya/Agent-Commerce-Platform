package com.comp5348.store.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CheckoutView(
        Long checkoutId,
        Long userId,
        String status,
        int quoteVersion,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        String currency,
        String shippingAddress,
        Long orderId,
        Instant expiresAt,
        List<Line> items
) {
    public record Line(Long skuId, int quantity, BigDecimal unitPrice) {}
}
