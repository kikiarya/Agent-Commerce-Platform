// repository/WarehouseRepository.java
package com.comp5348.store.repository;
import com.comp5348.store.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {}
