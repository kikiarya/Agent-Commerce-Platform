package com.comp5348.delivery.dto;

public record EmailNotification(
        Long orderId,
        String type,
        String toEmail,
        String subject,
        String body
) {}
