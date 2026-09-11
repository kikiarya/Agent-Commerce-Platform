package com.comp5348.bank.client;

import com.comp5348.common.grpc.StoreServiceGrpc;
import com.comp5348.common.grpc.StoreServiceOuterClass;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StoreGrpcClient {
    private static final Logger log = LoggerFactory.getLogger(StoreGrpcClient.class);
    
    @GrpcClient("storeservice")
    private StoreServiceGrpc.StoreServiceBlockingStub storeServiceStub;
    
    /**
     * Get user email by order ID
     */
    public Optional<String> getUserEmailByOrderId(Long orderId) {
        try {
            log.info("Calling gRPC GetUserEmailByOrderId for orderId: {}", orderId);
            
            StoreServiceOuterClass.GetUserEmailRequest request = 
                StoreServiceOuterClass.GetUserEmailRequest.newBuilder()
                    .setOrderId(orderId)
                    .build();
            
            StoreServiceOuterClass.GetUserEmailResponse response = 
                storeServiceStub.getUserEmailByOrderId(request);
            
            if (response.getFound()) {
                log.info("Found email for order {}: {}", orderId, response.getEmail());
                return Optional.of(response.getEmail());
            } else {
                log.warn("No email found for order: {}", orderId);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("Error getting user email via gRPC: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}



