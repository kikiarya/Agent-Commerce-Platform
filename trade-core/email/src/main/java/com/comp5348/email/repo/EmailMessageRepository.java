package com.comp5348.email.repo;

import com.comp5348.email.domain.EmailMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {
    java.util.Optional<EmailMessage> findByOrderIdAndTypeAndToAddr(Long orderId, String type, String toAddr);
}
