// repo/ShipmentItemRepository.java
package com.comp5348.delivery.repo;

import com.comp5348.delivery.domain.ShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, Long> {}
