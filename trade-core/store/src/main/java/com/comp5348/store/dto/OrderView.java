package com.comp5348.store.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * OrderView is a DTO for frontend response, not mapped to database.
 * Used to display order summary information and multi-warehouse allocation details.
 */
public class OrderView {

    private Long orderId;                // Order ID
    private String status;               // Order status (CREATED, PAID, FAILED, CANCELLED, etc.)
    private BigDecimal totalAmount;      // Order total amount
    private List<WarehouseAllocation> allocations; // Multi-warehouse allocation details
    private String refundStatus;
    private String paymentAttemptId;
    private Long checkoutId;
    private String shippingAddress;
    private BigDecimal shippingFee;
    private String currency;
    private Integer quoteVersion;
    private List<Line> items;

    /** A line is a warehouse allocation; the same SKU may span multiple warehouses. */
    public record Line(Long skuId, int quantity, BigDecimal unitPrice, Long warehouseStockId) {}

    // --- Constructors ---
    public OrderView(Long orderId, String status, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public OrderView(Long orderId, String status, BigDecimal totalAmount, List<WarehouseAllocation> allocations) {
        this.orderId = orderId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.allocations = allocations;
    }

    // --- Getter & Setter ---
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<WarehouseAllocation> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<WarehouseAllocation> allocations) {
        this.allocations = allocations;
    }

    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
    public String getPaymentAttemptId() { return paymentAttemptId; }
    public void setPaymentAttemptId(String paymentAttemptId) { this.paymentAttemptId = paymentAttemptId; }
    public Long getCheckoutId() { return checkoutId; }
    public void setCheckoutId(Long checkoutId) { this.checkoutId = checkoutId; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String value) { shippingAddress = value; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal value) { shippingFee = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public Integer getQuoteVersion() { return quoteVersion; }
    public void setQuoteVersion(Integer value) { quoteVersion = value; }
    public List<Line> getItems() { return items; }
    public void setItems(List<Line> value) { items = value; }
}
