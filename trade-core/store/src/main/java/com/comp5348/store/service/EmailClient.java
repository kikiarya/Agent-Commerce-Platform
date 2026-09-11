package com.comp5348.store.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class EmailClient {
    private static final Logger log = LoggerFactory.getLogger(EmailClient.class);

    private final RestClient http;
    private final String base;

    public EmailClient(RestClient http, @Value("${store.email-base}") String base) {
        this.http = http;
        // Allow config to have trailing slash or not
        this.base = base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Send email; return true on success, only log on failure without throwing (to avoid affecting order flow). */
    public boolean send(Long orderId, String type, String to, String subject, String body) {
        Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "type", type,
                "to", to,
                "subject", subject,
                "body", body
        );

        try {
            http.post()
                    .uri(base + "/api/email/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email sent: orderId={}, type={}, to={}", orderId, type, to);
            return true;
        } catch (RestClientException ex) {
            log.warn("Email send failed: orderId={}, type={}, to={}, err={}",
                    orderId, type, to, ex.getMessage());
            return false;
        }
    }
}
