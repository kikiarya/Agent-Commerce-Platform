package com.comp5348.store.repository;

import com.comp5348.store.model.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, Long> {
    Optional<DeliveryEvent> findByShipmentIdAndEventType(Long shipmentId, String eventType);
}
