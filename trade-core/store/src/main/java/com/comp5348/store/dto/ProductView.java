package com.comp5348.store.dto;

import java.math.BigDecimal;

public record ProductView(
    Long id,
    String sku,
    String name,
    BigDecimal price
) {}




