package com.comp5348.bank.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "payment_txn",
        uniqueConstraints = @UniqueConstraint(name="uk_idem_key", columnNames = "idempotency_key")
)
public class PaymentTxn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16)
    private TxType type;                 // PAYMENT / REFUND

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16)
    private TxStatus status;             // SUCCESS / FAILED / REFUNDED

    @Column(name="order_id", nullable=false)
    private Long orderId;

    @Column(nullable=false, precision=18, scale=2)
    private BigDecimal amount;

    @Column(name="idempotency_key", nullable=false, length=64)
    private String idempotencyKey;       // Idempotency key (unique)
    
    @Column(name="bank_tx_id", length=64)
    private String bankTxId;
    
    @Column(name="from_account_id")
    private Long fromAccountId;          // From account ID
    
    @Column(name="to_account_id")
    private Long toAccountId;            // To account ID
    
    @Column(name="balance_after", precision=18, scale=2)
    private BigDecimal balanceAfter;     // Balance after transaction (from account balance)
    
    @Column(length=255)
    private String description;          // Transaction description

    @Column(name="created_at", nullable=false)
    private Instant createdAt = Instant.now();

    // getters & setters ...
    public Long getId() { return id; }
    public TxType getType() { return type; }
    public void setType(TxType type) { this.type = type; }
    public TxStatus getStatus() { return status; }
    public void setStatus(TxStatus status) { this.status = status; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getBankTxId() { return bankTxId; }
    public void setBankTxId(String bankTxId) { this.bankTxId = bankTxId; }
    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }
    public Long getToAccountId() { return toAccountId; }
    public void setToAccountId(Long toAccountId) { this.toAccountId = toAccountId; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
