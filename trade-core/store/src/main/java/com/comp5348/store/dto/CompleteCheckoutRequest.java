package com.comp5348.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record CompleteCheckoutRequest(
        @NotBlank @Size(max = 48) String idempotencyKey,
        @Min(1) int quoteVersion,
        String bankMock
) {}
