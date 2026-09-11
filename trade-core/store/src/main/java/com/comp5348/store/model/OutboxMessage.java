package com.comp5348.store.model;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="outbox_message") @Getter @Setter
public class OutboxMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String routingKey;
    @Column(nullable=false, columnDefinition="text") private String payload;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    private Instant sentAt;
    private int attempts;
    @Column(length=1000) private String lastError;
}
