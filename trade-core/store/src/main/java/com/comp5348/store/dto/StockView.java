package com.comp5348.store.dto;

public record StockView(
    Long id,
    Long warehouseId,
    String warehouseName,
    Long productId,
    String productName,
    Integer quantity
) {}




