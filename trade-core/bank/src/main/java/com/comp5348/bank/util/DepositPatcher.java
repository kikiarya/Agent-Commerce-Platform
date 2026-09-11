package com.comp5348.bank.util;

import com.comp5348.bank.domain.BankAccount;
import com.comp5348.bank.domain.PaymentTxn;
import com.comp5348.bank.domain.TxStatus;
import com.comp5348.bank.domain.TxType;
import com.comp5348.bank.repo.BankAccountRepository;
import com.comp5348.bank.repo.PaymentTxnRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * One-time patch: Add initial deposit record for existing accounts
 * Usage: Add --spring.profiles.active=patch when starting
 * Example: gradle bootRun --args='--spring.profiles.active=patch'
 */
@Component
@Profile("patch")
public class DepositPatcher {
    private static final Logger log = LoggerFactory.getLogger(DepositPatcher.class);
    
    private final BankAccountRepository accountRepo;
    private final PaymentTxnRepository txnRepo;
    
    public DepositPatcher(BankAccountRepository accountRepo, PaymentTxnRepository txnRepo) {
        this.accountRepo = accountRepo;
        this.txnRepo = txnRepo;
    }
    
    @PostConstruct
    public void patchExistingAccounts() {
        log.info("=== Running Deposit Patcher ===");
        
        List<BankAccount> allAccounts = accountRepo.findAll();
        log.info("Found {} accounts to check", allAccounts.size());
        
        for (BankAccount account : allAccounts) {
            String idemKey = "INITIAL-DEPOSIT-" + account.getAccountNumber();
            
            // Check if initial deposit record already exists
            if (txnRepo.findByIdempotencyKey(idemKey).isPresent()) {
                log.info("Account {} already has initial deposit record, skipping", account.getAccountNumber());
                continue;
            }
            
            // If account balance is greater than 0 but no initial deposit record exists, supplement it
            if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                PaymentTxn txn = new PaymentTxn();
                txn.setType(TxType.DEPOSIT);
                txn.setStatus(TxStatus.SUCCESS);
                txn.setOrderId(0L);
                txn.setAmount(account.getBalance());
                txn.setIdempotencyKey(idemKey);
                txn.setBankTxId("DEP-" + account.getAccountNumber() + "-PATCHED");
                txn.setToAccountId(account.getId());
                txn.setBalanceAfter(account.getBalance());
                txn.setDescription("Initial deposit for existing account (patched)");
                
                txnRepo.save(txn);
                
                log.info("Added initial deposit record for account {}: balance={}", 
                        account.getAccountNumber(), account.getBalance());
            } else {
                log.info("Account {} has zero balance, no deposit record needed", account.getAccountNumber());
            }
        }
        
        log.info("=== Deposit Patching Complete ===");
    }
}

