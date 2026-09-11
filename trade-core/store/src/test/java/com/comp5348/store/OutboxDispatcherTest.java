package com.comp5348.store;
import com.comp5348.store.service.*;
import com.comp5348.store.model.OutboxMessage;
import com.comp5348.store.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OutboxDispatcherTest {
    OutboxRepository repo; RabbitTemplate rabbit; OutboxMessage event; OutboxDispatcher dispatcher;
    @BeforeEach void setup() {
        repo=mock(OutboxRepository.class);rabbit=mock(RabbitTemplate.class);
        event=new OutboxMessage();event.setId(1L);event.setRoutingKey("delivery.request");event.setPayload("{}");
        when(repo.findTop50BySentAtIsNullOrderByIdAsc()).thenReturn(List.of(event));
        dispatcher=new OutboxDispatcher(repo,rabbit,mock(OrderRepository.class),mock(WebSocketNotificationService.class),new ObjectMapper());
    }
    @Test void unavailableBrokerLeavesEventPending() {
        doThrow(new IllegalStateException("offline")).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        dispatcher.dispatch();assertNull(event.getSentAt());assertEquals(1,event.getAttempts());assertNotNull(event.getLastError());verify(repo).save(event);
    }
    @Test void confirmedMessageIsMarkedSent() {
        doAnswer(call->{CorrelationData c=call.getArgument(3);c.getFuture().complete(new CorrelationData.Confirm(true,null));return null;})
            .when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        dispatcher.dispatch();assertNotNull(event.getSentAt());assertNull(event.getLastError());
    }
    @Test void unroutableMessageIsNotMarkedSentEvenWhenAcked() {
        doAnswer(call->{CorrelationData c=call.getArgument(3); c.setReturned(new ReturnedMessage(call.getArgument(2),312,"NO_ROUTE","order-exchange","delivery.request"));
            c.getFuture().complete(new CorrelationData.Confirm(true,null));return null;})
            .when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        dispatcher.dispatch();assertNull(event.getSentAt());assertNotNull(event.getLastError());
    }
}
