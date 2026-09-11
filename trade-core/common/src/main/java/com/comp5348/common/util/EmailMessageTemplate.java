package com.comp5348.common.util;

import java.util.Map;

/**
 * Email message templates for different order and shipment statuses
 */
public class EmailMessageTemplate {
    
    /**
     * Get email subject and body for order status
     */
    public static EmailContent getOrderStatusMessage(String status) {
        Map<String, EmailContent> messages = Map.ofEntries(
            Map.entry("CREATED", new EmailContent("Order Created", "Your order has been created successfully.")),
            Map.entry("PAID", new EmailContent("Payment Success", "Your order is paid")),
            Map.entry("FULFILLED", new EmailContent("Order Delivered", "Your order has been delivered successfully.")),
            Map.entry("FAILED", new EmailContent("Order Failed", "Bank payment failed")),
            Map.entry("ORDER_FAILED", new EmailContent("Order Failed", "Bank payment failed")),
            Map.entry("CANCELLED", new EmailContent("Order Cancelled", "Your order has been cancelled")),
            Map.entry("DELIVERED", new EmailContent("Order Delivered", "Your order has been delivered.")),
            Map.entry("LOST", new EmailContent("Delivery Issue", "Your order is lost in transit and has been cancelled.")),
            Map.entry("REFUNDED", new EmailContent("Refund Processed", "Your payment has been successfully refunded.")),
            Map.entry("REFUND_FAILED", new EmailContent("Refund Failed", "We encountered an issue processing your refund. Please contact customer service.")),
            Map.entry("PACKAGE_MISSING", new EmailContent("Package Missing", "Package missing, your money has been refunded"))
        );
        
        return messages.getOrDefault(status, new EmailContent("Order Update", "Your order status has been updated."));
    }
    
    /**
     * Get email subject and body for shipment status
     */
    public static EmailContent getShipmentStatusMessage(String status) {
        Map<String, EmailContent> messages = Map.of(
            "REQUESTED", new EmailContent("Shipment Requested", "Your order shipment has been requested."),
            "PICKUP", new EmailContent("Order Picked Up", "Your order has been picked up from the warehouse."),
            "IN_TRANSIT", new EmailContent("Order In Transit", "Your order is on its way to you."),
            "OUT_FOR_DELIVERY", new EmailContent("Out for Delivery", "Your order is out for delivery today."),
            "DELIVERED", new EmailContent("Order Delivered", "Your order has been delivered successfully."),
            "LOST", new EmailContent("Delivery Issue", "Your package has been lost in transit. A refund has been issued.")
        );
        
        return messages.getOrDefault(status, new EmailContent("Shipment Update", "Your shipment status has been updated."));
    }
    
    /**
     * Get email content for any status (automatically detects if it's order or shipment status)
     */
    public static EmailContent getMessage(String status) {
        // Try order status first
        EmailContent orderMessage = getOrderStatusMessage(status);
        if (!orderMessage.subject().equals("Order Update")) {
            return orderMessage;
        }
        
        // Try shipment status
        EmailContent shipmentMessage = getShipmentStatusMessage(status);
        if (!shipmentMessage.subject().equals("Shipment Update")) {
            return shipmentMessage;
        }
        
        // Default message
        return orderMessage;
    }
    
    /**
     * Email content holder
     */
    public record EmailContent(String subject, String body) {}
}

