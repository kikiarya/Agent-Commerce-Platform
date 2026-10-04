package com.comp5348.store.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateCheckoutRequest(
        @Valid @Size(min = 1, max = 100) List<CreateCheckoutRequest.CheckoutItemRequest> items,
        @Size(max = 512) String shippingAddress
) {}
