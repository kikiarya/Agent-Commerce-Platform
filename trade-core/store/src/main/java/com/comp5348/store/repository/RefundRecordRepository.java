package com.comp5348.store.repository;

import com.comp5348.store.model.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {
    Optional<RefundRecord> findByOrderId(Long orderId);
    Optional<RefundRecord> findByIdempotencyKey(String key);
}
