package com.comp5348.store.repository;

import com.comp5348.store.model.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByPaymentAttemptId(String paymentAttemptId);
    Optional<PaymentAttempt> findByOrderId(Long orderId);
}
