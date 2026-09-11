package com.comp5348.email.service;

import com.comp5348.email.domain.EmailMessage;
import com.comp5348.email.repo.EmailMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final EmailMessageRepository repo;

    public EmailService(EmailMessageRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public EmailMessage send(Long orderId, String type, String to, String subject, String body) {
        var existing = repo.findByOrderIdAndTypeAndToAddr(orderId, type, to);
        if (existing.isPresent()) return existing.get();
        // Real project would connect to SMTP or cloud service; simplified to database save + logging
        log.info("EMAIL SENT type={} orderId={} to={} subject={}, body={}", type, orderId, to, subject, body);
        EmailMessage m = new EmailMessage();
        m.setOrderId(orderId);
        m.setType(type);
        m.setToAddr(to);
        m.setSubject(subject);
        m.setBody(body);
        m.setStatus("SENT");
        return repo.save(m);
    }
}
