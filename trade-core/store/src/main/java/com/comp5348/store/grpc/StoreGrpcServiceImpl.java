package com.comp5348.store.grpc;

import com.comp5348.common.grpc.StoreServiceGrpc;
import com.comp5348.common.grpc.StoreServiceOuterClass;
import com.comp5348.store.model.Order;
import com.comp5348.store.model.User;
import com.comp5348.store.repository.OrderRepository;
import com.comp5348.store.repository.UserRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

@GrpcService
public class StoreGrpcServiceImpl extends StoreServiceGrpc.StoreServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(StoreGrpcServiceImpl.class);
    
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    
    public StoreGrpcServiceImpl(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }
    
    @Override
    public void getUserEmailByOrderId(
            StoreServiceOuterClass.GetUserEmailRequest request,
            StreamObserver<StoreServiceOuterClass.GetUserEmailResponse> responseObserver) {
        
        log.info("gRPC: GetUserEmailByOrderId request for orderId: {}", request.getOrderId());
        
        try {
            Order order = orderRepository.findById(request.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("Order not found: {}", request.getOrderId());
                responseObserver.onNext(
                    StoreServiceOuterClass.GetUserEmailResponse.newBuilder()
                        .setFound(false)
                        .build()
                );
                responseObserver.onCompleted();
                return;
            }
            
            User user = userRepository.findById(order.getUserId()).orElse(null);
            if (user == null) {
                log.warn("User not found for order: {}", request.getOrderId());
                responseObserver.onNext(
                    StoreServiceOuterClass.GetUserEmailResponse.newBuilder()
                        .setFound(false)
                        .build()
                );
                responseObserver.onCompleted();
                return;
            }
            
            log.info("Found email for order {}: {}", request.getOrderId(), user.getEmail());
            
            responseObserver.onNext(
                StoreServiceOuterClass.GetUserEmailResponse.newBuilder()
                    .setFound(true)
                    .setEmail(user.getEmail())
                    .build()
            );
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error getting user email: {}", e.getMessage(), e);
            responseObserver.onNext(
                StoreServiceOuterClass.GetUserEmailResponse.newBuilder()
                    .setFound(false)
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void getOrderStatus(
            StoreServiceOuterClass.GetOrderStatusRequest request,
            StreamObserver<StoreServiceOuterClass.GetOrderStatusResponse> responseObserver) {
        
        log.info("gRPC: GetOrderStatus request for orderId: {}", request.getOrderId());
        
        try {
            Order order = orderRepository.findById(request.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("Order not found: {}", request.getOrderId());
                responseObserver.onNext(
                    StoreServiceOuterClass.GetOrderStatusResponse.newBuilder()
                        .setFound(false)
                        .build()
                );
                responseObserver.onCompleted();
                return;
            }
            
            log.info("Found order status for order {}: {}", request.getOrderId(), order.getStatus());
            
            responseObserver.onNext(
                StoreServiceOuterClass.GetOrderStatusResponse.newBuilder()
                    .setFound(true)
                    .setStatus(order.getStatus())
                    .build()
            );
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error getting order status: {}", e.getMessage(), e);
            responseObserver.onNext(
                StoreServiceOuterClass.GetOrderStatusResponse.newBuilder()
                    .setFound(false)
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void getOrderInfo(
            StoreServiceOuterClass.GetOrderInfoRequest request,
            StreamObserver<StoreServiceOuterClass.GetOrderInfoResponse> responseObserver) {
        
        log.info("gRPC: GetOrderInfo request for orderId: {}", request.getOrderId());
        
        try {
            Order order = orderRepository.findById(request.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("Order not found: {}", request.getOrderId());
                responseObserver.onNext(
                    StoreServiceOuterClass.GetOrderInfoResponse.newBuilder()
                        .setFound(false)
                        .build()
                );
                responseObserver.onCompleted();
                return;
            }
            
            User user = userRepository.findById(order.getUserId()).orElse(null);
            if (user == null) {
                log.warn("User not found for order: {}", request.getOrderId());
                responseObserver.onNext(
                    StoreServiceOuterClass.GetOrderInfoResponse.newBuilder()
                        .setFound(false)
                        .build()
                );
                responseObserver.onCompleted();
                return;
            }
            
            log.info("Found order info for order {}: userId={}, amount={}, email={}", 
                    request.getOrderId(), order.getUserId(), order.getTotalAmount(), user.getEmail());
            
            responseObserver.onNext(
                StoreServiceOuterClass.GetOrderInfoResponse.newBuilder()
                    .setFound(true)
                    .setUserId(order.getUserId())
                    .setTotalAmount(order.getTotalAmount().toString())
                    .setEmail(user.getEmail())
                    .build()
            );
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error getting order info: {}", e.getMessage(), e);
            responseObserver.onNext(
                StoreServiceOuterClass.GetOrderInfoResponse.newBuilder()
                    .setFound(false)
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void updateOrderStatus(
            StoreServiceOuterClass.UpdateOrderStatusRequest request,
            StreamObserver<StoreServiceOuterClass.UpdateOrderStatusResponse> responseObserver) {
        
        log.info("gRPC: UpdateOrderStatus request for orderId: {}, status: {}", 
                request.getOrderId(), request.getStatus());
        
        try {
            Order order = orderRepository.findById(request.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("Order not found: {}", request.getOrderId());
                responseObserver.onNext(
                    StoreServiceOuterClass.UpdateOrderStatusResponse.newBuilder()
                        .setSuccess(false)
                        .build()
                );
                responseObserver.onCompleted();
                return;
            }
            
            order.setStatus(request.getStatus());
            orderRepository.save(order);
            
            log.info("Updated order {} status to: {}", request.getOrderId(), request.getStatus());
            
            responseObserver.onNext(
                StoreServiceOuterClass.UpdateOrderStatusResponse.newBuilder()
                    .setSuccess(true)
                    .build()
            );
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error updating order status: {}", e.getMessage(), e);
            responseObserver.onNext(
                StoreServiceOuterClass.UpdateOrderStatusResponse.newBuilder()
                    .setSuccess(false)
                    .build()
            );
            responseObserver.onCompleted();
        }
    }
}



