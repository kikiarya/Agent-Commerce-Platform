package com.comp5348.store.repository;

import com.comp5348.store.model.CheckoutLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CheckoutLineRepository extends JpaRepository<CheckoutLine, Long> {
    List<CheckoutLine> findByCheckoutId(Long checkoutId);
    void deleteByCheckoutId(Long checkoutId);
}
