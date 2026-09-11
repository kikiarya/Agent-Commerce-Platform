package com.comp5348.bank.api;

import com.comp5348.bank.domain.PaymentTxn;
import com.comp5348.bank.domain.TxStatus;
import com.comp5348.bank.service.BankService;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Payment/Refund API:
 * - Idempotent: remember and reuse results based on idempotencyKey
 * - Failure/Timeout: triggered via ?mock=fail or ?mock=timeout
 * - Real account: transfer from user account to merchant account
 */
@RestController
@RequestMapping("/api")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    
    private final BankService bankService;
    
    public PaymentController(BankService bankService) {
        this.bankService = bankService;
    }

    public record PayReq(Long userId, Long orderId, String idempotencyKey, BigDecimal amount) {}
    public record PayResp(String status, String bankTxId) {}

    @PostMapping("/payments")
    public ResponseEntity<PayResp> pay(@RequestParam(required = false) String mock,
                                       @RequestBody PayReq req) {
        if (req == null || req.idempotencyKey() == null || req.idempotencyKey().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        if (req.userId() == null) {
            log.error("userId is required for payment");
            return ResponseEntity.badRequest().build();
        }

        try {
            PaymentTxn txn = bankService.pay(req.userId(), req.orderId(), req.amount(), req.idempotencyKey(), mock);
            
            String status = txn.getStatus() == TxStatus.SUCCESS ? "SUCCESS" : "FAILED";
            PayResp resp = new PayResp(status, txn.getBankTxId());
            
            log.info("Payment orderId={} userId={} amount={} result={}", 
                    req.orderId(), req.userId(), req.amount(), resp.status());
            return ResponseEntity.ok(resp);
            
        } catch (InterruptedException e) {
            log.error("Payment interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return ResponseEntity.status(408).body(new PayResp("TIMEOUT", null));
        } catch (Exception e) {
            log.error("Payment error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new PayResp("FAILED", null));
        }
    }

    /**
     * Query payment/refund by idempotency key (paymentAttemptId).
     * Used by Store Reconciler when pay() response was lost / timed out.
     */
    @GetMapping("/payments/by-key/{idempotencyKey}")
    public ResponseEntity<PayResp> queryByKey(@PathVariable String idempotencyKey) {
        return bankService.findByIdempotencyKey(idempotencyKey)
                .map(txn -> {
                    String status = switch (txn.getStatus()) {
                        case SUCCESS -> "SUCCESS";
                        case FAILED -> "FAILED";
                        case REFUNDED -> "REFUNDED";
                    };
                    return ResponseEntity.ok(new PayResp(status, txn.getBankTxId()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/refunds")
    public ResponseEntity<PayResp> refund(@RequestBody PayReq req) {
        if (req == null || req.idempotencyKey() == null || req.idempotencyKey().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        if (req.userId() == null) {
            log.error("userId is required for refund");
            return ResponseEntity.badRequest().build();
        }

        try {
            PaymentTxn txn = bankService.refund(req.userId(), req.orderId(), req.amount(), req.idempotencyKey());
            
            String status = txn.getStatus() == TxStatus.SUCCESS ? "REFUNDED" : "FAILED";
            PayResp resp = new PayResp(status, txn.getBankTxId());
            
            log.info("Refund orderId={} userId={} amount={} result={}", 
                    req.orderId(), req.userId(), req.amount(), resp.status());
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            log.error("Refund error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new PayResp("FAILED", null));
        }
    }
}
