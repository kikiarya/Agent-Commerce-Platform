package com.comp5348.store.exception;

public class InsufficientStockException extends RuntimeException {
    private final Long productId;
    private final int available;
    private final int requested;

    public InsufficientStockException(Long productId, int available, int requested) {
        super(String.format("Insufficient stock for product %d: available=%d, requested=%d",
                productId, available, requested));
        this.productId = productId;
        this.available = available;
        this.requested = requested;
    }

    public Long getProductId() { return productId; }
    public int getAvailable() { return available; }
    public int getRequested() { return requested; }
}

