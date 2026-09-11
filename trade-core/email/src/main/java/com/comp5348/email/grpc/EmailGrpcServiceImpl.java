package com.comp5348.email.grpc;

import com.comp5348.common.grpc.EmailServiceGrpc;
import com.comp5348.common.grpc.EmailServiceOuterClass;
import com.comp5348.common.util.EmailMessageTemplate;
import com.comp5348.email.client.StoreClient;
import com.comp5348.email.service.EmailService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

@GrpcService
public class EmailGrpcServiceImpl extends EmailServiceGrpc.EmailServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(EmailGrpcServiceImpl.class);

    private final EmailService emailService;
    private final StoreClient storeClient;

    public EmailGrpcServiceImpl(EmailService emailService, StoreClient storeClient) {
        this.emailService = emailService;
        this.storeClient = storeClient;
    }

    @Override
    public void sendShipmentNotification(
            EmailServiceOuterClass.SendShipmentNotificationRequest request,
            StreamObserver<EmailServiceOuterClass.SendShipmentNotificationResponse> responseObserver) {

        log.info("gRPC: SendShipmentNotification request for orderId: {}, shipmentId: {}, status: {}",
                request.getOrderId(), request.getShipmentId(), request.getStatus());

        try {
            // Check if order is cancelled before sending any notification
            boolean isOrderCancelled = storeClient.isOrderCancelled(request.getOrderId());
            if (isOrderCancelled) {
                log.info("Order {} is CANCELLED, skipping shipment notification for status {}", 
                        request.getOrderId(), request.getStatus());
                responseObserver.onNext(
                    EmailServiceOuterClass.SendShipmentNotificationResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Order is cancelled, notification skipped")
                        .build()
                );
                responseObserver.onCompleted();
                return;
            }

            // Get email template for shipment status
            var emailContent = EmailMessageTemplate.getShipmentStatusMessage(request.getStatus());

            // Send email notification
            emailService.send(
                request.getOrderId(),
                request.getStatus(),
                request.getUserEmail(),
                emailContent.subject(),
                emailContent.body()
            );

            // Double check order status before sending callback (order might have been cancelled just now)
            isOrderCancelled = storeClient.isOrderCancelled(request.getOrderId());
            if (isOrderCancelled) {
                log.info("Order {} was cancelled after email was sent, skipping callback", request.getOrderId());
                responseObserver.onNext(
                    EmailServiceOuterClass.SendShipmentNotificationResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Order cancelled, callback skipped")
                        .build()
                );
                responseObserver.onCompleted();
                return;
            }

            // Send callback to Store service to update order status
            boolean callbackSuccess = storeClient.sendDeliveryCallback(
                request.getShipmentId(),
                request.getOrderId(),
                request.getStatus()
            );

            if (!callbackSuccess) {
                throw new IllegalStateException("Store callback failed; retry notification");
            }

            responseObserver.onNext(
                EmailServiceOuterClass.SendShipmentNotificationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Notification sent successfully")
                    .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error sending shipment notification: {}", e.getMessage(), e);
            responseObserver.onNext(
                EmailServiceOuterClass.SendShipmentNotificationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + e.getMessage())
                    .build()
            );
            responseObserver.onCompleted();
        }
    }

    @Override
    public void sendDeliveryLossNotification(
            EmailServiceOuterClass.SendDeliveryLossNotificationRequest request,
            StreamObserver<EmailServiceOuterClass.SendDeliveryLossNotificationResponse> responseObserver) {

        log.info("gRPC: SendDeliveryLossNotification request for orderId: {}, shipmentId: {}",
                request.getOrderId(), request.getShipmentId());

        try {
            // Send package missing email
            var emailContent = EmailMessageTemplate.getOrderStatusMessage("PACKAGE_MISSING");
            emailService.send(
                request.getOrderId(),
                "PACKAGE_MISSING",
                request.getUserEmail(),
                emailContent.subject(),
                emailContent.body()
            );

            // Send callback to Store for LOST status
            boolean callbackSuccess = storeClient.sendDeliveryCallback(
                request.getShipmentId(),
                request.getOrderId(),
                "LOST"
            );

            if (!callbackSuccess) {
                throw new IllegalStateException("Store LOST callback failed; retry notification");
            }

            // The accepted LOST callback enqueues the refund in Store's outbox.

            responseObserver.onNext(
                EmailServiceOuterClass.SendDeliveryLossNotificationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Delivery loss notification sent successfully")
                    .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error sending delivery loss notification: {}", e.getMessage(), e);
            responseObserver.onNext(
                EmailServiceOuterClass.SendDeliveryLossNotificationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + e.getMessage())
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
}