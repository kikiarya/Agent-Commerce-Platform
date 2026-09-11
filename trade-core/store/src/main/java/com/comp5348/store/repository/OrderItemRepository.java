// repository/OrderItemRepository.java
package com.comp5348.store.repository;
import com.comp5348.store.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    java.util.List<OrderItem> findByOrderId(Long orderId);
}
