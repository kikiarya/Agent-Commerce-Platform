package com.comp5348.bank.util;

import com.comp5348.bank.domain.AccountType;
import com.comp5348.bank.service.AccountService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    
    private final AccountService accountService;
    
    public DataInitializer(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @PostConstruct
    public void init() {
        log.info("=== Initializing Bank Data ===");
        
        // Create Customer account (userId=1, assuming this is the customer user ID in Store service)
        if (accountService.findByUserId(1L).isEmpty()) {
            var customerAccount = accountService.createAccount(
                "Customer User", 
                AccountType.PERSONAL, 
                1L, 
                new BigDecimal("10000.00")  // Initial balance $10,000
            );
            log.info("Created customer bank account: {} with balance: {}", 
                    customerAccount.getAccountNumber(), customerAccount.getBalance());
        } else {
            log.info("Customer bank account already exists");
        }
        
        // Create Admin account (userId=2, assuming this is the admin user ID in Store service)
        if (accountService.findByUserId(2L).isEmpty()) {
            var adminAccount = accountService.createAccount(
                "Admin User", 
                AccountType.PERSONAL, 
                2L, 
                new BigDecimal("10000.00")  // Initial balance $10,000
            );
            log.info("Created admin bank account: {} with balance: {}", 
                    adminAccount.getAccountNumber(), adminAccount.getBalance());
        } else {
            log.info("Admin bank account already exists");
        }
        
        // Create merchant account (userId=0, indicates this is a merchant account)
        if (accountService.findByUserId(0L).isEmpty()) {
            var merchantAccount = accountService.createAccount(
                "Distributed Commerce Platform",
                AccountType.MERCHANT, 
                0L, 
                BigDecimal.ZERO  // Initial balance $0
            );
            log.info("Created merchant account: {} with balance: {}", 
                    merchantAccount.getAccountNumber(), merchantAccount.getBalance());
        } else {
            log.info("Merchant account already exists");
        }
        
        log.info("=== Bank Data Initialization Complete ===");
        
        // Output all account information
        var allAccounts = accountService.getAllAccounts();
        log.info("Total accounts created: {}", allAccounts.size());
        for (var account : allAccounts) {
            log.info("  - Account: {}, Holder: {}, Type: {}, Balance: {}", 
                    account.getAccountNumber(), account.getHolderName(), 
                    account.getAccountType(), account.getBalance());
        }
    }
}

