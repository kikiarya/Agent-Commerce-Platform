package com.comp5348.store.service;
import com.comp5348.store.dto.*;
import com.comp5348.store.model.OutboxMessage;
import com.comp5348.store.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagePublisher {
    private final OutboxRepository outbox;
    private final ObjectMapper json;
    public MessagePublisher(OutboxRepository outbox, ObjectMapper json) { this.outbox = outbox; this.json = json; }
    @Transactional
    public void publishRefundRequest(RefundRequest request) { enqueue("refund.request", request); }
    @Transactional
    public void publishDeliveryRequest(DeliveryRequest request) { enqueue("delivery.request", request); }
    @Transactional
    public void publishEmailNotification(EmailNotification request) { enqueue("email.notification", request); }
    private void enqueue(String key, Object payload) {
        try {
            OutboxMessage message = new OutboxMessage();
            message.setRoutingKey(key); message.setPayload(json.writeValueAsString(payload));
            outbox.save(message);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize outbox message", e);
        }
    }
}
