// repo/ShipmentRepository.java
package com.comp5348.delivery.repo;

import com.comp5348.delivery.domain.Shipment;
import com.comp5348.delivery.domain.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    java.util.Optional<Shipment> findByOrderId(Long orderId);
    List<Shipment> findByStatusIn(List<ShipmentStatus> statuses);
}
