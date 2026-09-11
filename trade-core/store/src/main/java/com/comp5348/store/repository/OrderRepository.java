package com.comp5348.store.repository;
import com.comp5348.store.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdempotencyKey(String key);
    List<Order> findTop50ByStatusOrderByIdAsc(String status);
    List<Order> findAllByOrderByIdDesc();
    List<Order> findByUserIdOrderByIdDesc(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findLockedById(@Param("id") Long id);
}
