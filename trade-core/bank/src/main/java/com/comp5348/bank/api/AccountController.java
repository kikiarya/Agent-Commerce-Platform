package com.comp5348.bank.api;

import com.comp5348.bank.domain.AccountType;
import com.comp5348.bank.domain.BankAccount;
import com.comp5348.bank.domain.PaymentTxn;
import com.comp5348.bank.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Bank account management API
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    
    private final AccountService accountService;
    
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }
    
    // DTO records
    public record CreateAccountReq(String holderName, AccountType accountType, Long userId, BigDecimal initialBalance) {}
    public record AccountResp(Long id, String accountNumber, String holderName, BigDecimal balance, 
                             String accountType, String status, Long userId) {}
    public record DepositReq(BigDecimal amount, String description) {}
    
    /**
     * Create bank account
     */
    @PostMapping
    public ResponseEntity<AccountResp> createAccount(@RequestBody CreateAccountReq req) {
        try {
            if (req.holderName() == null || req.holderName().isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            
            BigDecimal initialBalance = req.initialBalance() != null ? req.initialBalance() : BigDecimal.ZERO;
            BankAccount account = accountService.createAccount(
                req.holderName(), 
                req.accountType(), 
                req.userId(), 
                initialBalance
            );
            
            log.info("Account created via API: {} for user: {}", account.getAccountNumber(), req.holderName());
            return ResponseEntity.ok(toResp(account));
            
        } catch (Exception e) {
            log.error("Error creating account: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Query account by user ID
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<AccountResp> getAccountByUserId(@PathVariable Long userId) {
        Optional<BankAccount> account = accountService.findByUserId(userId);
        return account.map(acc -> ResponseEntity.ok(toResp(acc)))
                     .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Query account by account number
     */
    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountResp> getAccountByNumber(@PathVariable String accountNumber) {
        Optional<BankAccount> account = accountService.findByAccountNumber(accountNumber);
        return account.map(acc -> ResponseEntity.ok(toResp(acc)))
                     .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Query account by account ID
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResp> getAccount(@PathVariable Long accountId) {
        Optional<BankAccount> account = accountService.findById(accountId);
        return account.map(acc -> ResponseEntity.ok(toResp(acc)))
                     .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Query account balance
     */
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable Long accountId) {
        try {
            BigDecimal balance = accountService.getBalance(accountId);
            return ResponseEntity.ok(balance);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Query account transaction history
     */
    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<List<PaymentTxn>> getTransactionHistory(@PathVariable Long accountId) {
        try {
            List<PaymentTxn> transactions = accountService.getTransactionHistory(accountId);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            log.error("Error fetching transaction history: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Deposit (for testing)
     */
    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<AccountResp> deposit(@PathVariable Long accountId, @RequestBody DepositReq req) {
        try {
            if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().build();
            }
            
            BankAccount account = accountService.deposit(accountId, req.amount(), req.description());
            log.info("Deposit successful: {} to account {}", req.amount(), accountId);
            return ResponseEntity.ok(toResp(account));
            
        } catch (IllegalArgumentException e) {
            log.error("Deposit error: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing deposit: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Query all accounts
     */
    @GetMapping
    public ResponseEntity<List<AccountResp>> getAllAccounts() {
        List<BankAccount> accounts = accountService.getAllAccounts();
        List<AccountResp> responses = accounts.stream().map(this::toResp).toList();
        return ResponseEntity.ok(responses);
    }
    
    // Helper method to convert entity to response
    private AccountResp toResp(BankAccount account) {
        return new AccountResp(
            account.getId(),
            account.getAccountNumber(),
            account.getHolderName(),
            account.getBalance(),
            account.getAccountType().name(),
            account.getStatus().name(),
            account.getUserId()
        );
    }
}

