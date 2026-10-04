// repository/ProductRepository.java
package com.comp5348.store.repository;
import com.comp5348.store.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select p from Product p where p.id = :id")
    java.util.Optional<Product> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
