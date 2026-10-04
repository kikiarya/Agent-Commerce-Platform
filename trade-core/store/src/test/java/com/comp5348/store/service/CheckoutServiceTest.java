package com.comp5348.store.service;

import com.comp5348.store.dto.*;
import com.comp5348.store.model.*;
import com.comp5348.store.repository.*;
import com.comp5348.store.exception.CheckoutConflictException;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CheckoutServiceTest {
    CheckoutSessionRepository sessions = mock(CheckoutSessionRepository.class);
    CheckoutLineRepository lines = mock(CheckoutLineRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    OrderService orders = mock(OrderService.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    CheckoutService service = new CheckoutService(sessions, lines, products, orders, manager);
    CheckoutSession session;
    CompleteCheckoutRequest request = new CompleteCheckoutRequest("checkout-key", 1, null);
    OrderView order = new OrderView(10L, "PAYMENT_PENDING", new BigDecimal("60"), null);

    @BeforeEach void setup() {
        session = new CheckoutSession(); session.setId(1L); session.setUserId(2L);
        session.setStatus("CONFIRMED"); session.setQuoteVersion(1);
        session.setShippingAddress("Test address"); session.setTotalAmount(new BigDecimal("60"));
        session.setExpiresAt(Instant.now().plusSeconds(600));
        when(sessions.findLockedById(1L)).thenReturn(Optional.of(session));
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        CheckoutLine line = new CheckoutLine(); line.setProductId(3L); line.setQuantity(1);
        line.setUnitPrice(new BigDecimal("50"));
        when(lines.findByCheckoutId(1L)).thenReturn(List.of(line));
        Product product = new Product("SKU", "Test", new BigDecimal("50")); product.setId(3L);
        when(products.findLockedById(3L)).thenReturn(Optional.of(product));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(orders.reserveConfirmedCheckout(any(), anyList(), eq("checkout-key"), isNull()))
                .thenReturn(order);
        when(orders.getForUser(10L, 2L)).thenReturn(order);
    }

    @Test void commitPrecedesRemotePaymentAndReplayReusesOrder() {
        assertEquals(10L, service.complete(2L, 1L, request).getOrderId());
        var ordered = inOrder(manager, orders);
        ordered.verify(orders).reserveConfirmedCheckout(any(), anyList(), anyString(), isNull());
        ordered.verify(manager).commit(any());
        ordered.verify(orders).settlePayment(10L);
        session.setExpiresAt(Instant.now().minusSeconds(60));
        assertEquals(10L, service.complete(2L, 1L, request).getOrderId());
        verify(orders, times(1)).reserveConfirmedCheckout(any(), anyList(), anyString(), any());
    }

    @Test void staleConfirmationIsRejected() {
        session.setStatus("QUOTED"); session.setQuoteVersion(2);
        assertThrows(CheckoutConflictException.class, () -> service.confirm(2L, 1L, 1));
        assertEquals("QUOTED", session.getStatus());
    }

    @Test void priceChangeDoesNotCreateOrder() {
        when(products.findLockedById(3L)).thenReturn(Optional.of(new Product("SKU", "Test", new BigDecimal("55"))));
        assertThrows(CheckoutConflictException.class, () -> service.complete(2L, 1L, request));
        verifyNoInteractions(orders);
        verify(manager).rollback(any());
    }

    @Test void completionRequiresConfirmationAndCurrentVersion() {
        session.setStatus("QUOTED");
        assertThrows(IllegalStateException.class, () -> service.complete(2L, 1L, request));
        session.setStatus("CONFIRMED"); session.setQuoteVersion(2);
        assertThrows(CheckoutConflictException.class, () -> service.complete(2L, 1L, request));
        verifyNoInteractions(orders);
    }

    @Test void completedSessionRejectsDifferentKey() {
        session.setStatus("COMPLETED"); session.setOrderId(10L); session.setCompletionKey("original");
        assertThrows(CheckoutConflictException.class, () -> service.complete(2L, 1L, request));
        verifyNoInteractions(orders);
    }

    @Test void paymentFailureLeavesDurablePendingOrderForRecovery() {
        doThrow(new RuntimeException("network unavailable")).when(orders).settlePayment(10L);
        assertEquals("PAYMENT_PENDING", service.complete(2L, 1L, request).getStatus());
        assertEquals(10L, session.getOrderId());
        verify(manager).commit(any()); verify(manager, never()).rollback(any());
    }

    @Test void expiredReadShowsStateWithoutWritingOrLocking() {
        session.setExpiresAt(Instant.now().minusSeconds(1));
        assertEquals("EXPIRED", service.get(2L, 1L).status());
        verify(sessions, never()).save(any()); verify(sessions, never()).findLockedById(anyLong());
    }

    @Test void foreignCheckoutAndInvalidQuantitiesAreRejected() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.get(999L, 1L));
        var invalid = new UpdateCheckoutRequest(List.of(new CreateCheckoutRequest.CheckoutItemRequest(3L, -1)), null);
        assertThrows(IllegalArgumentException.class, () -> service.update(2L, 1L, invalid));
        verify(lines, never()).deleteByCheckoutId(anyLong());
    }
}
