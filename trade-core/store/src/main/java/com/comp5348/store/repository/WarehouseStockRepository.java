// repository/WarehouseStockRepository.java
package com.comp5348.store.repository;
import com.comp5348.store.model.WarehouseStock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {
    // Select a warehouse that can satisfy the quantity
    Optional<WarehouseStock> findFirstByProductIdAndQuantityGreaterThanEqualOrderByQuantityDesc(Long productId, Integer qty);
    List<WarehouseStock> findByWarehouseId(Long warehouseId);
    List<WarehouseStock> findByProductIdAndQuantityGreaterThanOrderByQuantityDesc(Long productId, Integer quantity);
    List<WarehouseStock> findByProductId(Long productId);
}
