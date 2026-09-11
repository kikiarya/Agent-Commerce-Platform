package com.comp5348.bank.service;

import com.comp5348.bank.domain.AccountStatus;
import com.comp5348.bank.domain.AccountType;
import com.comp5348.bank.domain.BankAccount;
import com.comp5348.bank.domain.PaymentTxn;
import com.comp5348.bank.domain.TxStatus;
import com.comp5348.bank.domain.TxType;
import com.comp5348.bank.repo.BankAccountRepository;
import com.comp5348.bank.repo.PaymentTxnRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final SecureRandom random = new SecureRandom();
    
    private final BankAccountRepository accountRepo;
    private final PaymentTxnRepository txnRepo;
    
    public AccountService(BankAccountRepository accountRepo, PaymentTxnRepository txnRepo) {
        this.accountRepo = accountRepo;
        this.txnRepo = txnRepo;
    }
    
    /**
     * Create bank account
     */
    @Transactional
    public BankAccount createAccount(String holderName, AccountType accountType, Long userId, BigDecimal initialBalance) {
        String accountNumber = generateAccountNumber();
        
        BankAccount account = new BankAccount(accountNumber, holderName, accountType, userId, initialBalance);
        BankAccount saved = accountRepo.save(account);
        
        log.info("Created bank account: {} for user: {} with initial balance: {}", 
                accountNumber, holderName, initialBalance);
        
        // If there is initial balance, record a deposit transaction
        if (initialBalance != null && initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            PaymentTxn txn = new PaymentTxn();
            txn.setType(TxType.DEPOSIT);
            txn.setStatus(TxStatus.SUCCESS);
            txn.setOrderId(0L); // Initial deposit has no order ID
            txn.setAmount(initialBalance);
            txn.setIdempotencyKey("INITIAL-DEPOSIT-" + accountNumber);
            txn.setBankTxId("DEP-" + accountNumber);
            txn.setToAccountId(saved.getId());
            txn.setBalanceAfter(initialBalance);
            txn.setDescription("Initial deposit for new account");
            txnRepo.save(txn);
        }
        
        return saved;
    }
    
    /**
     * Query account by user ID
     */
    public Optional<BankAccount> findByUserId(Long userId) {
        return accountRepo.findByUserId(userId);
    }
    
    /**
     * Query account by account number
     */
    public Optional<BankAccount> findByAccountNumber(String accountNumber) {
        return accountRepo.findByAccountNumber(accountNumber);
    }
    
    /**
     * Query account by account ID
     */
    public Optional<BankAccount> findById(Long accountId) {
        return accountRepo.findById(accountId);
    }
    
    /**
     * Query account balance
     */
    public BigDecimal getBalance(Long accountId) {
        return accountRepo.findById(accountId)
                .map(BankAccount::getBalance)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }
    
    /**
     * Query account transaction history
     */
    public List<PaymentTxn> getTransactionHistory(Long accountId) {
        return txnRepo.findAll().stream()
                .filter(txn -> accountId.equals(txn.getFromAccountId()) || accountId.equals(txn.getToAccountId()))
                .toList();
    }
    
    /**
     * Deposit
     */
    @Transactional
    public BankAccount deposit(Long accountId, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        
        BankAccount account = accountRepo.findLockedById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }
        
        account.credit(amount);
        BankAccount saved = accountRepo.save(account);
        
        // Record transaction
        PaymentTxn txn = new PaymentTxn();
        txn.setType(TxType.DEPOSIT);
        txn.setStatus(TxStatus.SUCCESS);
        txn.setOrderId(0L);
        txn.setAmount(amount);
        txn.setIdempotencyKey("DEPOSIT-" + accountId + "-" + System.currentTimeMillis());
        txn.setBankTxId("DEP-" + System.currentTimeMillis());
        txn.setToAccountId(accountId);
        txn.setBalanceAfter(saved.getBalance());
        txn.setDescription(description != null ? description : "Deposit");
        txnRepo.save(txn);
        
        log.info("Deposited {} to account {}, new balance: {}", amount, accountId, saved.getBalance());
        
        return saved;
    }
    
    /**
     * Transfer (internal use, for payment and refund)
     */
    @Transactional
    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount, 
                        String idempotencyKey, TxType txType, Long orderId, String description) {
        
        // Idempotency check
        Optional<PaymentTxn> existed = txnRepo.findByIdempotencyKey(idempotencyKey);
        if (existed.isPresent()) {
            log.info("Transaction already exists with idempotency key: {}", idempotencyKey);
            return;
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        
        BankAccount first = accountRepo.findLockedById(Math.min(fromAccountId, toAccountId)).orElseThrow();
        BankAccount second = accountRepo.findLockedById(Math.max(fromAccountId, toAccountId)).orElseThrow();
        // Another transaction may have committed while these locks were acquired.
        if (txnRepo.findByIdempotencyKey(idempotencyKey).isPresent()) return;
        BankAccount fromAccount = first.getId().equals(fromAccountId) ? first : second;
        BankAccount toAccount = first.getId().equals(toAccountId) ? first : second;
        
        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("From account is not active");
        }
        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("To account is not active");
        }
        
        // Check balance
        if (!fromAccount.hasSufficientBalance(amount)) {
            throw new IllegalArgumentException("Insufficient balance in account: " + fromAccountId);
        }
        
        // Debit and credit
        fromAccount.debit(amount);
        toAccount.credit(amount);
        
        accountRepo.save(fromAccount);
        accountRepo.save(toAccount);
        
        // Record transaction
        PaymentTxn txn = new PaymentTxn();
        txn.setType(txType);
        txn.setStatus(TxStatus.SUCCESS);
        txn.setOrderId(orderId);
        txn.setAmount(amount);
        txn.setIdempotencyKey(idempotencyKey);
        txn.setBankTxId(generateTxId(txType));
        txn.setFromAccountId(fromAccountId);
        txn.setToAccountId(toAccountId);
        txn.setBalanceAfter(fromAccount.getBalance());
        txn.setDescription(description);
        txnRepo.save(txn);
        
        log.info("Transfer completed: {} from account {} to account {}, type: {}", 
                amount, fromAccountId, toAccountId, txType);
    }
    
    /**
     * Generate account number
     */
    private String generateAccountNumber() {
        // Format: BANK-XXXXXXXXXX (10 random digits)
        long number = 1000000000L + (long)(random.nextDouble() * 9000000000L);
        return "BANK-" + number;
    }
    
    /**
     * Generate transaction ID
     */
    private String generateTxId(TxType txType) {
        String prefix = switch(txType) {
            case PAYMENT -> "PAY";
            case REFUND -> "REF";
            case DEPOSIT -> "DEP";
        };
        return prefix + "-" + System.currentTimeMillis() + "-" + random.nextInt(1000);
    }
    
    /**
     * Get all accounts
     */
    public List<BankAccount> getAllAccounts() {
        return accountRepo.findAll();
    }
}

