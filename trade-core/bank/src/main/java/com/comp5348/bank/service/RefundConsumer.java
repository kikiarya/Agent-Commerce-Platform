package com.comp5348.bank.service;

import com.comp5348.bank.client.StoreGrpcClient;
import com.comp5348.bank.config.RabbitMQConfig;
import com.comp5348.bank.dto.EmailNotification;
import com.comp5348.bank.dto.RefundRequest;
import com.comp5348.common.util.EmailMessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefundConsumer {
    private static final Logger log = LoggerFactory.getLogger(RefundConsumer.class);
    
    private final BankService bankService;
    private final RabbitTemplate rabbitTemplate;
    private final StoreGrpcClient storeGrpcClient;

    public RefundConsumer(BankService bankService, RabbitTemplate rabbitTemplate, StoreGrpcClient storeGrpcClient) {
        this.bankService = bankService;
        this.rabbitTemplate = rabbitTemplate;
        this.storeGrpcClient = storeGrpcClient;
    }

    @RabbitListener(queues = RabbitMQConfig.REFUND_REQUEST_QUEUE)
    public void processRefundRequest(RefundRequest refundRequest) {
        log.info("Processing refund request for orderId: {}, userId: {}, amount: {}", 
            refundRequest.orderId(), refundRequest.userId(), refundRequest.amount());
        
        try {
            // Process the refund
            var refundTxn = bankService.refund(
                refundRequest.userId(),
                refundRequest.orderId(), 
                refundRequest.amount(), 
                refundRequest.idempotencyKey()
            );
            
            if (refundTxn.getStatus() != com.comp5348.bank.domain.TxStatus.SUCCESS)
                throw new IllegalStateException("Refund transaction is not successful");
            log.info("Refund processed successfully for orderId: {}, userId: {}, status: {}", 
                refundRequest.orderId(), refundRequest.userId(), refundTxn.getStatus());
            
            // Resolve real user email via gRPC
            String toEmail = storeGrpcClient.getUserEmailByOrderId(refundRequest.orderId())
                    .orElse("customer@example.com");

            log.info("To email for orderId {}: {}", refundRequest.orderId(), toEmail);

            // Send success email notification
            var emailContent = EmailMessageTemplate.getOrderStatusMessage("REFUNDED");
            EmailNotification emailNotification = new EmailNotification(
                refundRequest.orderId(),
                "REFUNDED",
                toEmail,
                emailContent.subject(),
                emailContent.body()
            );
            
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                "email.notification",
                emailNotification
            );
            
            log.info("Published refund success email for orderId: {}", refundRequest.orderId());
            
        } catch (Exception e) {
            log.error("Failed to process refund for orderId: {}, error: {}", 
                refundRequest.orderId(), e.getMessage(), e);
            
            // Resolve real user email via gRPC
            String toEmailFailed = storeGrpcClient.getUserEmailByOrderId(refundRequest.orderId())
                    .orElse("customer@example.com");

            // Send failure email notification
            var emailContentFailed = EmailMessageTemplate.getOrderStatusMessage("REFUND_FAILED");
            EmailNotification emailNotification = new EmailNotification(
                refundRequest.orderId(),
                "REFUND_FAILED",
                toEmailFailed,
                emailContentFailed.subject(),
                emailContentFailed.body()
            );
            
            try {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_EXCHANGE,
                    "email.notification",
                    emailNotification
                );
                log.info("Published refund failure email for orderId: {}", refundRequest.orderId());
            } catch (Exception emailError) {
                log.error("Failed to send refund failure email for orderId: {}", refundRequest.orderId(), emailError);
            }
            throw new IllegalStateException("Refund processing must be retried", e);
        }
    }
}
