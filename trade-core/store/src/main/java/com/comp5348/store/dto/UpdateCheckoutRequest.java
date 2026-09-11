package com.comp5348.store.dto;

import java.util.List;

public record UpdateCheckoutRequest(
        List<CreateCheckoutRequest.CheckoutItemRequest> items,
        String shippingAddress
) {}
