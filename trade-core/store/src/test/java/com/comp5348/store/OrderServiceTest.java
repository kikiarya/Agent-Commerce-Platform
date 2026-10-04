package com.comp5348.store;
import com.comp5348.store.service.*;
import com.comp5348.store.model.*;
import com.comp5348.store.model.Order;
import com.comp5348.store.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    OrderRepository orders; OrderItemRepository items; InventoryService inventory; BankClient bank;
    MessagePublisher publisher; PlatformTransactionManager manager; OrderService service; Order order;
    PaymentAttemptRepository attempts; RefundRecordRepository refunds;

    @BeforeEach void setup() {
        orders=mock(OrderRepository.class); items=mock(OrderItemRepository.class); inventory=mock(InventoryService.class);
        bank=mock(BankClient.class); publisher=mock(MessagePublisher.class); manager=mock(PlatformTransactionManager.class);
        attempts=mock(PaymentAttemptRepository.class); refunds=mock(RefundRecordRepository.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(attempts.findByOrderId(anyLong())).thenReturn(Optional.empty());
        when(refunds.findByOrderId(anyLong())).thenReturn(Optional.empty());
        when(bank.queryOutcome(anyString())).thenReturn(BankClient.PaymentOutcome.UNKNOWN);
        service=new OrderService(orders,items,mock(ProductRepository.class),inventory,bank,publisher,mock(UserRepository.class),attempts,refunds,manager);
        order=new Order(); order.setId(1L);order.setUserId(2L);order.setStatus("PAYMENT_PENDING");
        order.setTotalAmount(new BigDecimal("10"));order.setIdempotencyKey("same-key");order.setPaymentAttemptId("same-key");
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        when(orders.findLockedById(1L)).thenReturn(Optional.of(order));
    }

    @Test void unknownPaymentKeepsReservationAndCreatesNoMessages() {
        when(bank.payOutcome(anyLong(),anyLong(),any(),anyString(),any())).thenReturn(BankClient.PaymentOutcome.UNKNOWN);
        service.settlePayment(1L);
        assertEquals("PAYMENT_PENDING",order.getStatus());verifyNoInteractions(inventory,publisher);
        verify(manager,never()).commit(any());
    }

    @Test void querySuccessFinalizesWithoutRelyingOnPayReplayAlone() {
        when(bank.queryOutcome("same-key")).thenReturn(BankClient.PaymentOutcome.SUCCESS);
        when(items.findByOrderId(1L)).thenReturn(List.of());
        service.settlePayment(1L);
        assertEquals("PAID", order.getStatus());
        verify(bank, never()).payOutcome(anyLong(), anyLong(), any(), anyString(), any());
        verify(publisher, times(1)).publishDeliveryRequest(any());
    }

    @Test void explicitFailureRestocksExactlyOnceOnRepeatedRecovery() {
        OrderItem item=new OrderItem();item.setWarehouseStockId(7L);item.setQty(3);
        when(items.findByOrderId(1L)).thenReturn(List.of(item));
        when(bank.payOutcome(anyLong(),anyLong(),any(),anyString(),any())).thenReturn(BankClient.PaymentOutcome.FAILED);
        service.settlePayment(1L);service.settlePayment(1L);
        assertEquals("FAILED",order.getStatus());verify(inventory,times(1)).restock(7L,3);
        verify(publisher,never()).publishDeliveryRequest(any());
    }

    @Test void successfulRecoveryQueuesDeliveryAndNotificationOnlyOnce() {
        when(bank.payOutcome(anyLong(),anyLong(),any(),anyString(),any())).thenReturn(BankClient.PaymentOutcome.SUCCESS);
        service.settlePayment(1L); service.settlePayment(1L);
        assertEquals("PAID",order.getStatus()); verify(publisher,times(1)).publishDeliveryRequest(any());
        verify(publisher,times(1)).publishEmailNotification(any());
    }

    @Test void cannotCancelFailedOrDeliveringOrder() {
        for(String state:List.of("FAILED","DELIVERING","FULFILLED")) {
            order.setStatus(state);assertThrows(IllegalStateException.class,()->service.cancelOrder(1L));
        }
    }

    @Test void pendingUnknownCannotCancel() {
        order.setStatus("PAYMENT_PENDING");
        when(bank.queryOutcome(anyString())).thenReturn(BankClient.PaymentOutcome.UNKNOWN);
        when(bank.payOutcome(anyLong(),anyLong(),any(),anyString(),any())).thenReturn(BankClient.PaymentOutcome.UNKNOWN);
        assertThrows(IllegalStateException.class, () -> service.cancelOrder(1L));
        verify(inventory, never()).restock(anyLong(), anyInt());
    }

    @Test void repeatedCancellationDoesNotRestockOrRefundTwice() {
        order.setStatus("PAID");OrderItem item=new OrderItem();item.setWarehouseStockId(7L);item.setQty(3);
        Product product = new Product("test", "Test", new BigDecimal("10")); product.setId(9L);
        item.setProduct(product); item.setPriceAtOrder(product.getPrice());
        when(items.findByOrderId(1L)).thenReturn(List.of(item));
        service.cancelOrder(1L);service.cancelOrder(1L);
        verify(inventory,times(1)).restock(7L,3);verify(publisher,times(1)).publishRefundRequest(argThat(r->r.idempotencyKey().equals("refund-1")));
    }

    @Test void existingIntentTransactionCommitsBeforeBankCall() {
        Product product=new Product();product.setId(9L);OrderItem item=new OrderItem();item.setProduct(product);item.setQty(2);
        when(items.findByOrderId(1L)).thenReturn(List.of(item));
        when(orders.findByIdempotencyKey("same-key")).thenReturn(Optional.of(order));
        when(bank.payOutcome(anyLong(),anyLong(),any(),anyString(),any())).thenReturn(BankClient.PaymentOutcome.UNKNOWN);
        service.placeSingleItem(2L,9L,2,"same-key",null);
        var sequence=inOrder(manager,bank);
        sequence.verify(manager).commit(any());
        sequence.verify(bank).queryOutcome("same-key");
        sequence.verify(bank).payOutcome(2L,1L,new BigDecimal("10"),"same-key",null);
    }

    @Test void reusedKeyWithDifferentRequestIsRejected() {
        when(orders.findByIdempotencyKey("same-key")).thenReturn(Optional.of(order));
        assertThrows(com.comp5348.store.exception.CheckoutConflictException.class,()->service.placeSingleItem(99L,9L,2,"same-key",null));
        verifyNoInteractions(bank,inventory,publisher);
    }

    @Test void rejectsMissingUserId() {
        assertThrows(IllegalArgumentException.class, () -> service.placeSingleItem(null, 1L, 1, "k", null));
    }
}
