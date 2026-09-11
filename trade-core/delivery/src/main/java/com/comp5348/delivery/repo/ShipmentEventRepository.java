// repo/ShipmentEventRepository.java
package com.comp5348.delivery.repo;

import com.comp5348.delivery.domain.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {}
