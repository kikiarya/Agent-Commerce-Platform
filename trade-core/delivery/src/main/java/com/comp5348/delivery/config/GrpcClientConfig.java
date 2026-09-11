package com.comp5348.delivery.config;

import com.comp5348.common.grpc.EmailServiceGrpc;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {
    private static final Logger log = LoggerFactory.getLogger(GrpcClientConfig.class);

    // Prefer injection from the starter if available
    @GrpcClient("emailservice")
    private Channel emailChannel;

    @Value("${grpc.client.emailservice.address:static://localhost:9091}")
    private String emailServiceAddress;

    @Value("${grpc.client.emailservice.negotiationType:plaintext}")
    private String negotiationType;

    @Bean
    public EmailServiceGrpc.EmailServiceBlockingStub emailServiceBlockingStub() {
        Channel channel = emailChannel;
        if (channel == null) {
            // Fallback: build channel manually from properties
            String target = emailServiceAddress;
            if (target.startsWith("static://")) {
                target = target.substring("static://".length());
            }
            log.warn("@GrpcClient injection not available. Building manual channel to {} (negotiation={})", target, negotiationType);
            ManagedChannel managedChannel = ManagedChannelBuilder
                    .forTarget(target)
                    .usePlaintext()
                    .build();
            channel = managedChannel;
        }
        return EmailServiceGrpc.newBlockingStub(channel);
    }
}
