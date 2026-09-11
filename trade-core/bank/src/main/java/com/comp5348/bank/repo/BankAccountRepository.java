package com.comp5348.bank.repo;

import com.comp5348.bank.domain.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from BankAccount a where a.id = :id")
    Optional<BankAccount> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<BankAccount> findByAccountNumber(String accountNumber);
    Optional<BankAccount> findByUserId(Long userId);
}

