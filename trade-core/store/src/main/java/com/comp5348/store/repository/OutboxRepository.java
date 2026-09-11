package com.comp5348.store.repository;
import com.comp5348.store.model.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findTop50BySentAtIsNullOrderByIdAsc();
}
