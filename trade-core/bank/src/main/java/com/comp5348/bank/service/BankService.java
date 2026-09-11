package com.comp5348.bank.service;

import com.comp5348.bank.domain.BankAccount;
import com.comp5348.bank.domain.PaymentTxn;
import com.comp5348.bank.domain.TxStatus;
import com.comp5348.bank.domain.TxType;
import com.comp5348.bank.repo.PaymentTxnRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class BankService {
    private static final Logger log = LoggerFactory.getLogger(BankService.class);
    private final PaymentTxnRepository txns;
    private final AccountService accountService;
    
    // Merchant account's userId
    private static final Long MERCHANT_USER_ID = 0L;

    public BankService(PaymentTxnRepository txns, AccountService accountService) { 
        this.txns = txns;
        this.accountService = accountService;
    }

    public Optional<PaymentTxn> findByIdempotencyKey(String idempotencyKey) {
        return txns.findByIdempotencyKey(idempotencyKey);
    }

    /**
     * Payment - transfer from user account to merchant account
     * @param userId User ID
     * @param orderId Order ID
     * @param amount Payment amount
     * @param idemKey Idempotency key
     * @param mock Mock parameter (fail/timeout)
     */
    @Transactional
    public PaymentTxn pay(Long userId, Long orderId, BigDecimal amount, String idemKey, String mock)
            throws InterruptedException {

        // Idempotency: replay with same key
        Optional<PaymentTxn> existed = txns.findByIdempotencyKey(idemKey);
        if (existed.isPresent()) {
            log.info("Payment already processed with idempotency key: {}", idemKey);
            return existed.get();
        }

        // Simulate timeout
        if ("timeout".equalsIgnoreCase(mock)) {
            log.warn("Simulating payment timeout for order: {}", orderId);
            Thread.sleep(8000);  // Simulate timeout
        }

        // Simulate failure
        if ("fail".equalsIgnoreCase(mock)) {
            log.warn("Simulating payment failure for order: {}", orderId);
        PaymentTxn t = new PaymentTxn();
        t.setType(TxType.PAYMENT);
        t.setOrderId(orderId);
        t.setAmount(amount);
        t.setIdempotencyKey(idemKey);
            t.setStatus(TxStatus.FAILED);
            t.setBankTxId(null);
            t.setDescription("Payment failed (simulated)");
            PaymentTxn saved = txns.save(t);
            log.info("PAYMENT orderId={} amount={} status=FAILED (simulated)", orderId, amount);
            return saved;
        }

        // Real payment flow: transfer from user account to merchant account
        try {
            // Get user account
            BankAccount userAccount = accountService.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User account not found for userId: " + userId));
            
            // Get merchant account
            BankAccount merchantAccount = accountService.findByUserId(MERCHANT_USER_ID)
                    .orElseThrow(() -> new IllegalArgumentException("Merchant account not found"));
            
            // Check balance
            if (!userAccount.hasSufficientBalance(amount)) {
                log.warn("Insufficient balance for userId: {}, balance: {}, required: {}", 
                        userId, userAccount.getBalance(), amount);
                
                PaymentTxn t = new PaymentTxn();
                t.setType(TxType.PAYMENT);
                t.setOrderId(orderId);
                t.setAmount(amount);
                t.setIdempotencyKey(idemKey);
                t.setStatus(TxStatus.FAILED);
                t.setBankTxId(null);
                t.setFromAccountId(userAccount.getId());
                t.setToAccountId(merchantAccount.getId());
                t.setBalanceAfter(userAccount.getBalance());
                t.setDescription("Payment failed: Insufficient balance");
                PaymentTxn saved = txns.save(t);
                log.info("PAYMENT orderId={} amount={} status=FAILED (insufficient balance)", orderId, amount);
                return saved;
            }
            
            // Execute transfer
            accountService.transfer(
                userAccount.getId(), 
                merchantAccount.getId(), 
                amount, 
                idemKey, 
                TxType.PAYMENT, 
                orderId,
                "Payment for order #" + orderId
            );
            
            // Return transaction record
            PaymentTxn saved = txns.findByIdempotencyKey(idemKey)
                    .orElseThrow(() -> new IllegalStateException("Transaction not found after transfer"));
            
            log.info("PAYMENT SUCCESS: orderId={} amount={} from account {} to merchant account {}", 
                    orderId, amount, userAccount.getAccountNumber(), merchantAccount.getAccountNumber());
            
            return saved;
            
        } catch (Exception e) {
            log.error("Payment error for order {}: {}", orderId, e.getMessage(), e);
            
            throw new IllegalStateException("Payment failed before commit; retry with the same key", e);
        }
    }

    /**
     * Refund - transfer from merchant account back to user account
     * @param userId User ID
     * @param orderId Order ID
     * @param amount Refund amount
     * @param idemKey Idempotency key
     */
    @Transactional
    public PaymentTxn refund(Long userId, Long orderId, BigDecimal amount, String idemKey) {
        Optional<PaymentTxn> existed = txns.findByIdempotencyKey(idemKey);
        if (existed.isPresent()) {
            log.info("Refund already processed with idempotency key: {}", idemKey);
            return existed.get();
        }

        try {
            // Get user account
            BankAccount userAccount = accountService.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User account not found for userId: " + userId));
            
            // Get merchant account
            BankAccount merchantAccount = accountService.findByUserId(MERCHANT_USER_ID)
                    .orElseThrow(() -> new IllegalArgumentException("Merchant account not found"));
            
            // Check merchant account balance (should be sufficient as payment was received earlier)
            if (!merchantAccount.hasSufficientBalance(amount)) {
                log.error("Merchant account has insufficient balance for refund. This should not happen!");
                throw new IllegalStateException("Merchant account insufficient balance");
            }
            
            // Execute transfer: from merchant account back to user account
            accountService.transfer(
                merchantAccount.getId(), 
                userAccount.getId(), 
                amount, 
                idemKey, 
                TxType.REFUND, 
                orderId,
                "Refund for order #" + orderId
            );
            
            // Return transaction record
            PaymentTxn saved = txns.findByIdempotencyKey(idemKey)
                    .orElseThrow(() -> new IllegalStateException("Transaction not found after refund"));
            
            log.info("REFUND SUCCESS: orderId={} amount={} from merchant account {} to account {}", 
                    orderId, amount, merchantAccount.getAccountNumber(), userAccount.getAccountNumber());
            
            return saved;
            
        } catch (Exception e) {
            log.error("Refund error for order {}: {}", orderId, e.getMessage(), e);
            
            throw new IllegalStateException("Refund failed; safe to retry with the same key", e);
        }
    }
}
