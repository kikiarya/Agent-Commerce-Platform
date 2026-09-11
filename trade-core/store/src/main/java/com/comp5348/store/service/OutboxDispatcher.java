package com.comp5348.store.service;
import com.comp5348.store.config.RabbitMQConfig;
import com.comp5348.store.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxRepository outbox;
    private final RabbitTemplate rabbit;
    private final OrderRepository orders;
    private final WebSocketNotificationService notifications;
    private final ObjectMapper json;
    public OutboxDispatcher(OutboxRepository outbox, RabbitTemplate rabbit, OrderRepository orders,
            WebSocketNotificationService notifications, ObjectMapper json) {
        this.outbox = outbox; this.rabbit = rabbit; this.orders = orders; this.notifications = notifications; this.json = json;
    }
    @Scheduled(fixedDelayString="${store.outbox-poll-ms:2000}")
    public void dispatch() {
        for (var event : outbox.findTop50BySentAtIsNullOrderByIdAsc()) {
            event.setAttempts(event.getAttempts() + 1);
            try {
                var props = new MessageProperties();
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                props.setMessageId("store-outbox-" + event.getId());
                var correlation = new CorrelationData(props.getMessageId());
                rabbit.send(RabbitMQConfig.ORDER_EXCHANGE, event.getRoutingKey(),
                        new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), props), correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null)
                    throw new IllegalStateException("Broker rejected or could not route event");
                event.setSentAt(Instant.now()); event.setLastError(null);
            } catch (Exception e) {
                event.setLastError(e.toString().substring(0, Math.min(e.toString().length(), 1000)));
                log.warn("Outbox event {} remains pending", event.getId(), e);
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }
            outbox.save(event);
            if (event.getSentAt() != null && "email.notification".equals(event.getRoutingKey())) {
                try {
                    var p = json.readTree(event.getPayload());
                    orders.findById(p.get("orderId").asLong()).ifPresent(order -> notifications.sendNotificationToUser(
                        order.getUserId(), p.get("type").asText(), p.get("subject").asText(), p.get("body").asText(), order.getId()));
                } catch (Exception e) { log.debug("Best-effort UI notification failed", e); }
            }
        }
    }
}
