package com.comp5348.store.dto;

/**
 * Warehouse allocation information - represents inventory allocated from a warehouse
 */
public record WarehouseAllocation(
    Long warehouseId,
    Long warehouseStockId,
    String warehouseName,
    Integer quantity
) {}

