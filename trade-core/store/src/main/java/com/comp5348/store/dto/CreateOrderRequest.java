// dto/CreateOrderRequest.java
package com.comp5348.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull Long productId,
        @Min(1) Integer quantity
) {}
