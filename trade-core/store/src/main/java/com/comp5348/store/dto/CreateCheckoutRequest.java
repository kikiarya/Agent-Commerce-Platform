package com.comp5348.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateCheckoutRequest(
        @NotEmpty @Valid List<CheckoutItemRequest> items,
        String shippingAddress
) {
    public record CheckoutItemRequest(
            @NotNull Long skuId,
            @Min(1) int quantity
    ) {}
}
