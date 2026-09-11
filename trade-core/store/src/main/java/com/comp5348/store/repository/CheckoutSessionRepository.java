package com.comp5348.store.repository;

import com.comp5348.store.model.CheckoutSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CheckoutSession c where c.id = :id")
    Optional<CheckoutSession> findLockedById(@Param("id") Long id);
}
