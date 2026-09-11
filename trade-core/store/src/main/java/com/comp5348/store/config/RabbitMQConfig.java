package com.comp5348.store.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Queue names
    public static final String REFUND_REQUEST_QUEUE = "refund-request-queue";
    public static final String EMAIL_NOTIFICATION_QUEUE = "email-notification-queue";
    public static final String EMAIL_DLQ = "email-notification-dlq";
    public static final String DELIVERY_REQUEST_QUEUE = "delivery-request-queue";

    // Exchange names
    public static final String ORDER_EXCHANGE = "order-exchange";

    @Bean
    public Queue refundRequestDlq() { return QueueBuilder.durable("refund-request-dlq").build(); }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue refundRequestQueue() {
        return QueueBuilder.durable(REFUND_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", "refund-request-dlq").build();
    }

    @Bean
    public Queue emailNotificationQueue() {
        return QueueBuilder.durable(EMAIL_NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", EMAIL_DLQ)
                .build();
    }
    
    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(EMAIL_DLQ).build();
    }

    @Bean
    public Queue deliveryRequestDlq() { return QueueBuilder.durable("delivery-request-dlq").build(); }

    @Bean
    public Queue deliveryRequestQueue() {
        return QueueBuilder.durable(DELIVERY_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", "delivery-request-dlq")
                .build();
    }

    @Bean
    public Binding refundRequestBinding() {
        return BindingBuilder.bind(refundRequestQueue())
                .to(orderExchange())
                .with("refund.request");
    }

    @Bean
    public Binding emailNotificationBinding() {
        return BindingBuilder.bind(emailNotificationQueue())
                .to(orderExchange())
                .with("email.notification");
    }

    @Bean
    public Binding deliveryRequestBinding() {
        return BindingBuilder.bind(deliveryRequestQueue())
                .to(orderExchange())
                .with("delivery.request");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setMandatory(true);
        if (connectionFactory instanceof org.springframework.amqp.rabbit.connection.CachingConnectionFactory caching) {
            caching.setPublisherConfirmType(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType.CORRELATED);
            caching.setPublisherReturns(true);
        }
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(org.springframework.amqp.rabbit.config.RetryInterceptorBuilder.stateless()
                .maxAttempts(4).backOffOptions(1000, 2.0, 10000)
                .recoverer(new org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer()).build());
        return factory;
    }
}
