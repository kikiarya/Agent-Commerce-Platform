package com.comp5348.store.dto;

import jakarta.validation.constraints.Min;

public record ConfirmCheckoutRequest(@Min(1) int quoteVersion) {}
