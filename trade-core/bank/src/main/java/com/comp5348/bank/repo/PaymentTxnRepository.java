package com.comp5348.bank.repo;

import com.comp5348.bank.domain.PaymentTxn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTxnRepository extends JpaRepository<PaymentTxn, Long> {
    Optional<PaymentTxn> findByIdempotencyKey(String idempotencyKey);
}
