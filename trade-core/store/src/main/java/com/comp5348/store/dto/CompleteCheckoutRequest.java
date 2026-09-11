package com.comp5348.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteCheckoutRequest(
        @NotBlank @Size(max = 48) String idempotencyKey,
        String bankMock
) {}
