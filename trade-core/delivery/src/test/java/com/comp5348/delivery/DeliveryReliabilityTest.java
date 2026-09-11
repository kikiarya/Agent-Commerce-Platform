package com.comp5348.delivery;
import com.comp5348.delivery.service.*;
import com.comp5348.delivery.repo.*;
import com.comp5348.delivery.client.*;
import com.comp5348.delivery.domain.*;
import com.comp5348.delivery.dto.DeliveryRequest;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DeliveryReliabilityTest {
    ShipmentRepository repo; StoreRestClient store; EmailGrpcClient email; DeliveryService service;
    @BeforeEach void setup(){
        repo=mock(ShipmentRepository.class);store=mock(StoreRestClient.class);email=mock(EmailGrpcClient.class);
        service=new DeliveryService(repo,mock(ShipmentItemRepository.class),mock(ShipmentEventRepository.class),
            mock(RestClient.class),"http://localhost",0,email,store);
    }
    @Test void duplicateRequestReturnsExistingShipment() {
        Shipment s=new Shipment();when(repo.findByOrderId(1L)).thenReturn(Optional.of(s));
        assertSame(s,service.createShipment(1L,List.of()));verify(repo,never()).save(any());verifyNoInteractions(store);
    }
    @Test void unavailableStatusRetriesInsteadOfAcknowledgingAndDiscarding() {
        when(store.getOrderStatus(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,()->new DeliveryRequestConsumer(service).processDeliveryRequest(new DeliveryRequest(1L,List.of())));
        verify(repo,never()).save(any());
    }
    @Test void cancelledOrderDoesNotCreateShipment() {
        when(store.getOrderStatus(1L)).thenReturn(Optional.of("CANCELLED"));
        assertNull(service.createShipment(1L,List.of()));verify(repo,never()).save(any());
    }
    @Test void failedCallbackKeepsTransitionForNextTick() {
        Shipment s=new Shipment();org.springframework.test.util.ReflectionTestUtils.setField(s,"id",4L);s.setOrderId(1L);
        when(repo.findByStatusIn(anyList())).thenReturn(List.of(s));
        when(store.getOrderStatus(1L)).thenReturn(Optional.of("PAID"));
        when(store.getOrderInfo(1L)).thenReturn(Optional.of(new StoreRestClient.OrderInfo(2L,java.math.BigDecimal.TEN,"x@example.com")));
        when(email.sendShipmentNotification(anyLong(),anyLong(),anyString(),anyString())).thenReturn(false,true);
        service.advanceAll();assertEquals(ShipmentStatus.REQUESTED,s.getStatus());assertEquals(ShipmentStatus.PICKUP,s.getPendingStatus());
        service.advanceAll();assertEquals(ShipmentStatus.PICKUP,s.getStatus());assertNull(s.getPendingStatus());
    }
}
