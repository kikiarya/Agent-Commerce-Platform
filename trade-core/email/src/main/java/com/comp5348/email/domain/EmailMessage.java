package com.comp5348.email.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_message", uniqueConstraints = @UniqueConstraint(columnNames={"order_id", "type", "to_addr"}))
public class EmailMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="order_id", nullable=false) private Long orderId;
    @Column(nullable=false)  private String type;     // PAID / FAILED / SHIPPED ...
    @Column(name="to_addr", nullable=false) private String toAddr;
    @Column(nullable=false)  private String subject;
    @Column(columnDefinition="text") private String body;

    @Column(nullable=false)  private String status = "SENT";  // Simplified: directly considered as sent successfully
    @Column(nullable=false)  private Instant createdAt = Instant.now();

    // getters / setters
    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getToAddr() { return toAddr; }
    public void setToAddr(String toAddr) { this.toAddr = toAddr; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
