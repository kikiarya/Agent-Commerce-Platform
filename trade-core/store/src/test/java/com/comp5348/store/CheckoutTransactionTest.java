package com.comp5348.store;

import com.comp5348.store.dto.*;
import com.comp5348.store.model.*;
import com.comp5348.store.model.Order;
import com.comp5348.store.repository.*;
import com.comp5348.store.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** H2 + real JPA transactions. Bank and external side effects are mocked. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false"})
@ContextConfiguration(classes = CheckoutTransactionTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheckoutTransactionTest {
    @Configuration @EnableAutoConfiguration @EntityScan(basePackageClasses = Order.class)
    @EnableJpaRepositories(basePackageClasses = OrderRepository.class)
    @Import({CheckoutService.class, OrderService.class, InventoryService.class})
    static class Config {}

    @Autowired CheckoutService checkouts;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired WarehouseStockRepository stocks;
    @Autowired CheckoutSessionRepository sessions;
    @Autowired OrderRepository orders;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired OrderItemRepository orderItems;
    @MockitoBean BankClient bank;
    @MockitoBean MessagePublisher publisher;
    @MockitoBean CacheInvalidationService invalidation;
    Long stockId;
    Long productId;

    @BeforeEach void setup() {
        var product = products.save(new Product(UUID.randomUUID().toString(), "Keyboard", new BigDecimal("50")));
        var warehouse = warehouses.save(new Warehouse("Demo", "Local"));
        stockId = stocks.save(new WarehouseStock(warehouse, product, 2)).getId();
        productId = product.getId();
        when(bank.queryOutcome(anyString())).thenReturn(BankClient.PaymentOutcome.UNKNOWN);
        when(bank.payOutcome(anyLong(), anyLong(), any(), anyString(), any())).thenReturn(BankClient.PaymentOutcome.UNKNOWN);
    }

    CheckoutView confirmed(int quantity) {
        var draft = checkouts.create(2L, new CreateCheckoutRequest(
                List.of(new CreateCheckoutRequest.CheckoutItemRequest(productId, quantity)), "Test address"));
        var quote = checkouts.quote(2L, draft.checkoutId());
        return checkouts.confirm(2L, draft.checkoutId(), quote.quoteVersion());
    }

    @Test void bankSeesCommittedOrderAndRetryDoesNotReserveTwice() {
        var checkout = confirmed(1);
        var key = UUID.randomUUID().toString();
        when(bank.payOutcome(eq(2L), anyLong(), any(), eq(key), isNull())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            Long orderId = call.getArgument(1);
            assertTrue(orders.findById(orderId).isPresent());
            assertTrue(attempts.findByOrderId(orderId).isPresent());
            assertEquals(orderId, sessions.findById(checkout.checkoutId()).orElseThrow().getOrderId());
            assertEquals(1, stocks.findById(stockId).orElseThrow().getQuantity());
            return BankClient.PaymentOutcome.UNKNOWN;
        });
        var req = new CompleteCheckoutRequest(key, checkout.quoteVersion(), null);
        var first = checkouts.complete(2L, checkout.checkoutId(), req);
        var replay = checkouts.complete(2L, checkout.checkoutId(), req);
        assertEquals(first.getOrderId(), replay.getOrderId());
        assertEquals("PAYMENT_PENDING", replay.getStatus());
        assertEquals(1, stocks.findById(stockId).orElseThrow().getQuantity());
    }

    @Test void rollbackAfterReservationNeverCallsBank() {
        var checkout = confirmed(1);
        long before = orders.count();
        doThrow(new IllegalStateException("injected local failure")).when(invalidation).stockChanged();
        assertThrows(IllegalStateException.class, () -> checkouts.complete(2L, checkout.checkoutId(),
                new CompleteCheckoutRequest(UUID.randomUUID().toString(), checkout.quoteVersion(), null)));
        assertEquals(2, stocks.findById(stockId).orElseThrow().getQuantity());
        assertEquals(before, orders.count());
        assertNull(sessions.findById(checkout.checkoutId()).orElseThrow().getOrderId());
        verifyNoInteractions(bank);
    }

    @Test void changedPriceRequiresFreshQuoteAndConfirmation() {
        var checkout = confirmed(1);
        var product = products.findById(productId).orElseThrow();
        product.setPrice(new BigDecimal("55")); products.save(product);
        assertThrows(com.comp5348.store.exception.CheckoutConflictException.class,
                () -> checkouts.complete(2L, checkout.checkoutId(),
                        new CompleteCheckoutRequest(UUID.randomUUID().toString(), checkout.quoteVersion(), null)));
        assertEquals(2, stocks.findById(stockId).orElseThrow().getQuantity());
        verifyNoInteractions(bank);
        var refreshed = checkouts.quote(2L, checkout.checkoutId());
        assertEquals(checkout.quoteVersion() + 1, refreshed.quoteVersion());
        assertEquals(0, new BigDecimal("65").compareTo(refreshed.totalAmount()));
    }

    CheckoutView cart(Long secondProductId, int secondQuantity) {
        var draft = checkouts.create(2L, new CreateCheckoutRequest(List.of(
                new CreateCheckoutRequest.CheckoutItemRequest(productId, 1),
                new CreateCheckoutRequest.CheckoutItemRequest(secondProductId, secondQuantity)), "Confirmed address"));
        var quote = checkouts.quote(2L, draft.checkoutId());
        return checkouts.confirm(2L, draft.checkoutId(), quote.quoteVersion());
    }

    WarehouseStock secondStock(int quantity) {
        var product = products.save(new Product(UUID.randomUUID().toString(), "Mouse", new BigDecimal("25")));
        var warehouse = warehouses.save(new Warehouse("Second", "Local"));
        return stocks.save(new WarehouseStock(warehouse, product, quantity));
    }

    @Test void multiLineCartCreatesOnePaymentWithImmutableSnapshots() {
        var second = secondStock(3);
        var checkout = cart(second.getProduct().getId(), 2);
        String key = UUID.randomUUID().toString();
        when(bank.payOutcome(anyLong(), anyLong(), any(), anyString(), any())).thenReturn(BankClient.PaymentOutcome.SUCCESS);
        var request = new CompleteCheckoutRequest(key, checkout.quoteVersion(), null);
        var first = checkouts.complete(2L, checkout.checkoutId(), request);
        var replay = checkouts.complete(2L, checkout.checkoutId(), request);
        assertEquals(first.getOrderId(), replay.getOrderId());
        assertEquals("PAID", replay.getStatus());
        var saved = orders.findById(first.getOrderId()).orElseThrow();
        assertEquals("Confirmed address", saved.getShippingAddress());
        assertEquals("CNY", saved.getCurrency());
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getShippingFee()));
        assertEquals(0, new BigDecimal("100").compareTo(saved.getTotalAmount()));
        assertEquals(2, orderItems.findByOrderId(saved.getId()).size());
        assertEquals(2, first.getItems().size());
        assertEquals("Confirmed address", first.getShippingAddress());
        var changed = products.findById(productId).orElseThrow();
        changed.setPrice(new BigDecimal("500")); products.save(changed);
        var unchanged = checkouts.complete(2L, checkout.checkoutId(), request);
        assertEquals(0, new BigDecimal("100").compareTo(unchanged.getTotalAmount()));
        assertTrue(unchanged.getItems().stream().anyMatch(line -> line.skuId().equals(productId)
                && line.unitPrice().compareTo(new BigDecimal("50")) == 0));
        assertEquals(1, stocks.findById(stockId).orElseThrow().getQuantity());
        assertEquals(1, stocks.findById(second.getId()).orElseThrow().getQuantity());
        verify(bank, times(1)).payOutcome(eq(2L), eq(saved.getId()), any(), eq(key), isNull());
        verify(publisher, times(1)).publishDeliveryRequest(any());
    }

    @Test void laterLineOutOfStockRollsBackEarlierReservationAndOrder() {
        var second = secondStock(0);
        var checkout = cart(second.getProduct().getId(), 1);
        long orderCount = orders.count(); long attemptCount = attempts.count();
        assertThrows(com.comp5348.store.exception.InsufficientStockException.class,
                () -> checkouts.complete(2L, checkout.checkoutId(),
                        new CompleteCheckoutRequest(UUID.randomUUID().toString(), checkout.quoteVersion(), null)));
        assertEquals(orderCount, orders.count()); assertEquals(attemptCount, attempts.count());
        assertEquals(2, stocks.findById(stockId).orElseThrow().getQuantity());
        assertNull(sessions.findById(checkout.checkoutId()).orElseThrow().getOrderId());
        verifyNoInteractions(bank, publisher);
    }

    @Test void failedPaymentRestoresAllLinesOnce() {
        var second = secondStock(3);
        var checkout = cart(second.getProduct().getId(), 2);
        when(bank.payOutcome(anyLong(), anyLong(), any(), anyString(), any())).thenReturn(BankClient.PaymentOutcome.FAILED);
        var request = new CompleteCheckoutRequest(UUID.randomUUID().toString(), checkout.quoteVersion(), null);
        var first = checkouts.complete(2L, checkout.checkoutId(), request);
        assertEquals("FAILED", first.getStatus());
        checkouts.complete(2L, checkout.checkoutId(), request);
        assertEquals(2, stocks.findById(stockId).orElseThrow().getQuantity());
        assertEquals(3, stocks.findById(second.getId()).orElseThrow().getQuantity());
        verify(publisher, never()).publishDeliveryRequest(any());
    }

    @Test void sameKeyCannotAttachAnotherCheckoutToExistingOrder() {
        var first = confirmed(1); var second = confirmed(1);
        String key = UUID.randomUUID().toString();
        checkouts.complete(2L, first.checkoutId(), new CompleteCheckoutRequest(key, first.quoteVersion(), null));
        assertThrows(com.comp5348.store.exception.CheckoutConflictException.class,
                () -> checkouts.complete(2L, second.checkoutId(), new CompleteCheckoutRequest(key, second.quoteVersion(), null)));
        assertNull(sessions.findById(second.checkoutId()).orElseThrow().getOrderId());
        assertEquals(1, stocks.findById(stockId).orElseThrow().getQuantity());
    }

    @Test void concurrentCompletionReturnsOneOrderAndReservesOnce() throws Exception {
        var second = secondStock(3);
        var checkout = cart(second.getProduct().getId(), 2);
        var request = new CompleteCheckoutRequest(UUID.randomUUID().toString(), checkout.quoteVersion(), null);
        var gate = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Long> call = () -> {
                gate.await(); return checkouts.complete(2L, checkout.checkoutId(), request).getOrderId();
            };
            var a = executor.submit(call); var b = executor.submit(call); gate.countDown();
            assertEquals(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, stocks.findById(stockId).orElseThrow().getQuantity());
            assertEquals(1, stocks.findById(second.getId()).orElseThrow().getQuantity());
        } finally { executor.shutdownNow(); }
    }
}
