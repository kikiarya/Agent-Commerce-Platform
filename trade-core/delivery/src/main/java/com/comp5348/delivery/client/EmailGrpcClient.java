package com.comp5348.delivery.client;

import com.comp5348.common.grpc.EmailServiceGrpc;
import com.comp5348.common.grpc.EmailServiceOuterClass;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailGrpcClient {
    private static final Logger log = LoggerFactory.getLogger(EmailGrpcClient.class);

    @GrpcClient("emailservice")
    private EmailServiceGrpc.EmailServiceBlockingStub emailServiceStub;

    /**
     * Send shipment status notification via gRPC to Email service
     */
    public boolean sendShipmentNotification(Long orderId, Long shipmentId, String status, String userEmail) {
        try {
            log.info("Calling gRPC SendShipmentNotification for orderId: {}, shipmentId: {}, status: {}",
                    orderId, shipmentId, status);

            EmailServiceOuterClass.SendShipmentNotificationRequest request =
                EmailServiceOuterClass.SendShipmentNotificationRequest.newBuilder()
                    .setOrderId(orderId)
                    .setShipmentId(shipmentId)
                    .setStatus(status)
                    .setUserEmail(userEmail)
                    .build();

            EmailServiceOuterClass.SendShipmentNotificationResponse response =
                emailServiceStub.sendShipmentNotification(request);

            log.info("Shipment notification sent: success={}, message={}",
                    response.getSuccess(), response.getMessage());

            return response.getSuccess();

        } catch (Exception e) {
            log.error("Error sending shipment notification via gRPC: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send delivery loss notification via gRPC to Email service
     */
    public boolean sendDeliveryLossNotification(Long orderId, Long shipmentId, Long userId,
                                                 String userEmail, String totalAmount) {
        try {
            log.info("Calling gRPC SendDeliveryLossNotification for orderId: {}, shipmentId: {}",
                    orderId, shipmentId);

            EmailServiceOuterClass.SendDeliveryLossNotificationRequest request =
                EmailServiceOuterClass.SendDeliveryLossNotificationRequest.newBuilder()
                    .setOrderId(orderId)
                    .setShipmentId(shipmentId)
                    .setUserId(userId)
                    .setUserEmail(userEmail)
                    .setTotalAmount(totalAmount)
                    .build();

            EmailServiceOuterClass.SendDeliveryLossNotificationResponse response =
                emailServiceStub.sendDeliveryLossNotification(request);

            log.info("Delivery loss notification sent: success={}, message={}",
                    response.getSuccess(), response.getMessage());

            return response.getSuccess();

        } catch (Exception e) {
            log.error("Error sending delivery loss notification via gRPC: {}", e.getMessage(), e);
            return false;
        }
    }
}
