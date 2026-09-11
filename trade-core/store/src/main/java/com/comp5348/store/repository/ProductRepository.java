// repository/ProductRepository.java
package com.comp5348.store.repository;
import com.comp5348.store.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product, Long> {}
