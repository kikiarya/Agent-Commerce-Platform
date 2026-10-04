package com.comp5348.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CreateCheckoutRequest(
        @NotEmpty @Size(max = 100) @Valid List<CheckoutItemRequest> items,
        @Size(max = 512) String shippingAddress
) {
    public record CheckoutItemRequest(
            @NotNull @Positive Long skuId,
            @Min(1) int quantity
    ) {}
}
