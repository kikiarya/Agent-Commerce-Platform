package com.comp5348.email.service;

import com.comp5348.email.config.RabbitMQConfig;
import com.comp5348.email.dto.EmailNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class EmailConsumer {
    private static final Logger log = LoggerFactory.getLogger(EmailConsumer.class);
    
    private final EmailService emailService;

    public EmailConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = RabbitMQConfig.EMAIL_NOTIFICATION_QUEUE)
    public void processEmailNotification(EmailNotification emailNotification) {
        log.info("Processing email notification for orderId: {}, type: {}, to: {}", 
            emailNotification.orderId(), emailNotification.type(), emailNotification.toEmail());
        
        try {
            // Process the email
            var emailMessage = emailService.send(
                emailNotification.orderId(),
                emailNotification.type(),
                emailNotification.toEmail(),
                emailNotification.subject(),
                emailNotification.body()
            );
            
            log.info("Email processed successfully for orderId: {}, emailId: {}, status: {}", 
                emailNotification.orderId(), emailMessage.getId(), emailMessage.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to process email for orderId: {}, error: {}", 
                emailNotification.orderId(), e.getMessage(), e);
            // Message will be sent to DLQ due to exception
            throw e; // Re-throw to trigger DLQ
        }
    }
}
