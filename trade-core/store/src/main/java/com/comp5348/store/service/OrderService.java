package com.comp5348.store.service;

import com.comp5348.common.util.EmailMessageTemplate;
import com.comp5348.store.dto.*;
import com.comp5348.store.model.*;
import com.comp5348.store.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final BankClient bank;
    private final MessagePublisher publisher;
    private final UserRepository users;
    private final PaymentAttemptRepository attempts;
    private final RefundRecordRepository refunds;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orders, OrderItemRepository items,
            ProductRepository products, InventoryService inventory, BankClient bank,
            MessagePublisher publisher, UserRepository users,
            PaymentAttemptRepository attempts, RefundRecordRepository refunds,
            PlatformTransactionManager manager) {
        this.orders = orders; this.items = items; this.products = products;
        this.inventory = inventory; this.bank = bank; this.publisher = publisher;
        this.users = users; this.attempts = attempts; this.refunds = refunds;
        this.tx = new TransactionTemplate(manager);
    }

    /** Legacy direct order API (stage-1 still available for debugging). */
    public OrderView placeSingleItem(Long userId, Long productId, int qty, String key, String mock) {
        requireUser(userId);
        return placeFromCheckout(userId, productId, qty, null, key, mock, null);
    }

    /**
     * Create PAYMENT_PENDING order + inventory hold + PaymentAttempt, then settle (may stay UNKNOWN).
     */
    public OrderView placeFromCheckout(Long userId, Long productId, int qty, BigDecimal totalAmount,
                                       String key, String mock, Long checkoutId) {
        OrderView pending = reserveFromCheckout(userId, productId, qty, totalAmount, key, mock, checkoutId);
        settlePayment(pending.getOrderId());
        return get(pending.getOrderId());
    }

    /** Local transaction only: callers must commit before settling a remote payment. */
    public OrderView reserveFromCheckout(Long userId, Long productId, int qty, BigDecimal totalAmount,
                                       String key, String mock, Long checkoutId) {
        requireUser(userId);
        if (key == null || key.isBlank() || key.length() > 48 || qty <= 0)
            throw new IllegalArgumentException("A nonblank idempotency key (max 48 chars) and positive quantity are required");

        Long id = tx.execute(status -> {
            var previous = orders.findByIdempotencyKey(key);
            if (previous.isPresent()) {
                Order order = previous.get();
                var lines = items.findByOrderId(order.getId());
                if (!order.getUserId().equals(userId) || lines.stream().anyMatch(i -> !i.getProduct().getId().equals(productId))
                        || lines.stream().mapToInt(OrderItem::getQty).sum() != qty
                        || !Objects.equals(order.getCheckoutId(), checkoutId)
                        || (totalAmount != null && totalAmount.compareTo(order.getTotalAmount()) != 0))
                    throw new com.comp5348.store.exception.CheckoutConflictException("Idempotency key was used for a different request");
                return order.getId();
            }
            Product product = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found"));
            var allocations = inventory.reserveStock(productId, qty);
            Order order = new Order();
            order.setUserId(userId);
            order.setStatus("PAYMENT_PENDING");
            order.setTotalAmount(totalAmount != null ? totalAmount : product.getPrice().multiply(BigDecimal.valueOf(qty)));
            order.setIdempotencyKey(key);
            order.setPaymentAttemptId(key);
            order.setPaymentMock(mock);
            order.setCheckoutId(checkoutId);
            orders.save(order);
            for (var allocation : allocations) {
                OrderItem item = new OrderItem();
                item.setOrder(order); item.setProduct(product); item.setQty(allocation.quantity());
                item.setPriceAtOrder(product.getPrice()); item.setWarehouseStockId(allocation.warehouseStockId());
                items.save(item);
            }
            PaymentAttempt attempt = new PaymentAttempt();
            attempt.setPaymentAttemptId(key);
            attempt.setOrderId(order.getId());
            attempt.setStatus("UNKNOWN");
            attempts.save(attempt);
            return order.getId();
        });
        return get(id);
    }

    /** One confirmed cart, one order and one payment intent, all in the caller's transaction. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public OrderView reserveConfirmedCheckout(CheckoutSession checkout, List<CheckoutLine> cart, String key, String mock) {
        requireUser(checkout.getUserId());
        if (key == null || key.isBlank() || key.length() > 48 || cart.isEmpty())
            throw new IllegalArgumentException("Valid checkout items and idempotency key are required");
        var previous = orders.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            Order existing = previous.get();
            if (!Objects.equals(existing.getCheckoutId(), checkout.getId())
                    || !existing.getUserId().equals(checkout.getUserId())
                    || !Objects.equals(existing.getQuoteVersion(), checkout.getQuoteVersion()))
                throw new com.comp5348.store.exception.CheckoutConflictException("Idempotency key belongs to another checkout");
            return view(existing);
        }
        Order order = new Order();
        order.setUserId(checkout.getUserId());
        order.setStatus("PAYMENT_PENDING");
        order.setTotalAmount(checkout.getTotalAmount());
        order.setShippingFee(checkout.getShippingFee());
        order.setShippingAddress(checkout.getShippingAddress());
        order.setCurrency(checkout.getCurrency());
        order.setQuoteVersion(checkout.getQuoteVersion());
        order.setCheckoutId(checkout.getId());
        order.setIdempotencyKey(key);
        order.setPaymentAttemptId(key);
        order.setPaymentMock(mock);
        orders.save(order);
        // Stable SKU ordering reduces lock inversions for overlapping carts.
        for (CheckoutLine line : cart.stream().sorted(Comparator.comparing(CheckoutLine::getProductId)).toList()) {
            Product product = products.findById(line.getProductId()).orElseThrow();
            for (var allocation : inventory.reserveStock(line.getProductId(), line.getQuantity())) {
                OrderItem item = new OrderItem();
                item.setOrder(order); item.setProduct(product); item.setQty(allocation.quantity());
                item.setPriceAtOrder(line.getUnitPrice()); item.setWarehouseStockId(allocation.warehouseStockId());
                items.save(item);
            }
        }
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentAttemptId(key); attempt.setOrderId(order.getId()); attempt.setStatus("UNKNOWN");
        attempts.save(attempt);
        return view(order);
    }

    public void settlePayment(Long id) {
        Order intent = orders.findById(id).orElseThrow();
        if (!"PAYMENT_PENDING".equals(intent.getStatus())) return;

        String attemptId = intent.getPaymentAttemptId() != null ? intent.getPaymentAttemptId() : intent.getIdempotencyKey();

        // Prefer query: if Bank already has a final result, do not treat timeout as failure.
        BankClient.PaymentOutcome outcome = bank.queryOutcome(attemptId);
        if (outcome == BankClient.PaymentOutcome.UNKNOWN) {
            outcome = bank.payOutcome(intent.getUserId(), id, intent.getTotalAmount(), attemptId, intent.getPaymentMock());
        }
        if (outcome == BankClient.PaymentOutcome.UNKNOWN) {
            attempts.findByOrderId(id).ifPresent(a -> {
                a.setStatus("UNKNOWN");
                attempts.save(a);
            });
            return;
        }

        BankClient.PaymentOutcome finalOutcome = outcome;
        tx.executeWithoutResult(status -> {
            Order order = orders.findLockedById(id).orElseThrow();
            if (!"PAYMENT_PENDING".equals(order.getStatus())) return;
            var orderLines = items.findByOrderId(id);
            if (finalOutcome == BankClient.PaymentOutcome.FAILED) {
                for (OrderItem item : orderLines) inventory.restock(item.getWarehouseStockId(), item.getQty());
                order.setStatus("FAILED");
                attempts.findByOrderId(id).ifPresent(a -> { a.setStatus("FAILED"); attempts.save(a); });
            } else {
                order.setStatus("PAID");
                attempts.findByOrderId(id).ifPresent(a -> { a.setStatus("SUCCESS"); attempts.save(a); });
                var shipmentItems = orderLines.stream().map(item -> {
                    var stock = inventory.getStock(item.getWarehouseStockId());
                    return Map.<String,Object>of("warehouseId", stock.getWarehouse().getId(),
                            "productId", item.getProduct().getId(), "qty", item.getQty());
                }).toList();
                publisher.publishDeliveryRequest(new DeliveryRequest(id, shipmentItems));
            }
            orders.save(order);
            notifyOrder(order, order.getStatus());
        });
    }

    @Scheduled(fixedDelayString = "${store.payment-recovery-ms:10000}")
    public void recoverPayments() {
        for (Order order : orders.findTop50ByStatusOrderByIdAsc("PAYMENT_PENDING")) {
            try { settlePayment(order.getId()); }
            catch (Exception e) { log.warn("Payment recovery pending for order {}", order.getId(), e); }
        }
    }

    /**
     * Unpaid pending: release stock after Bank confirms not SUCCESS.
     * Paid unshipped: cancel + refund workflow.
     */
    public OrderView cancelOrder(Long id, Long userId) {
        requireUser(userId);
        Order existing = orders.findById(id).orElse(null);
        if (existing == null) return null;
        if (!existing.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Order does not belong to user");
        }
        if ("CANCELLED".equals(existing.getStatus())) {
            return view(existing);
        }

        if ("PAYMENT_PENDING".equals(existing.getStatus())) {
            String attemptId = existing.getPaymentAttemptId() != null ? existing.getPaymentAttemptId() : existing.getIdempotencyKey();
            BankClient.PaymentOutcome bankStatus = bank.queryOutcome(attemptId);
            if (bankStatus == BankClient.PaymentOutcome.UNKNOWN) {
                // Try one settle/query cycle; if still unknown, refuse cancel (do not release stock)
                settlePayment(id);
                existing = orders.findById(id).orElseThrow();
                if ("PAYMENT_PENDING".equals(existing.getStatus())) {
                    throw new IllegalStateException("Payment still UNKNOWN; cannot cancel until Bank query returns a final status");
                }
                if ("PAID".equals(existing.getStatus())) {
                    return cancelPaidUnshipped(id, userId);
                }
                if ("FAILED".equals(existing.getStatus())) {
                    return view(existing);
                }
            } else if (bankStatus == BankClient.PaymentOutcome.SUCCESS) {
                settlePayment(id);
                return cancelPaidUnshipped(id, userId);
            } else {
                settlePayment(id); // mark FAILED + restock
                return getForUser(id, userId);
            }
        }

        if ("PAID".equals(existing.getStatus())) {
            return cancelPaidUnshipped(id, userId);
        }

        throw new IllegalStateException("Only unpaid-pending (after Bank final) or paid unshipped orders can be cancelled: " + existing.getStatus());
    }

    /** Backward-compatible cancel without user (internal/tests) — prefers order owner. */
    public OrderView cancelOrder(Long id) {
        Order order = orders.findById(id).orElse(null);
        if (order == null) return null;
        return cancelOrder(id, order.getUserId());
    }

    private OrderView cancelPaidUnshipped(Long id, Long userId) {
        return tx.execute(status -> {
            Order order = orders.findLockedById(id).orElse(null);
            if (order == null) return null;
            if (!order.getUserId().equals(userId)) {
                throw new org.springframework.security.access.AccessDeniedException("Order does not belong to user");
            }
            if ("CANCELLED".equals(order.getStatus())) return view(order);
            if (!"PAID".equals(order.getStatus())) {
                throw new IllegalStateException("Only a paid, not-yet-dispatched order can be cancelled: " + order.getStatus());
            }
            for (OrderItem item : items.findByOrderId(id)) inventory.restock(item.getWarehouseStockId(), item.getQty());
            String refundKey = "refund-" + id;
            if (refundKey.length() > 48) refundKey = refundKey.substring(0, 48);
            RefundRecord refund = refunds.findByOrderId(id).orElseGet(RefundRecord::new);
            if (refund.getId() == null) {
                refund.setOrderId(id);
                refund.setAmount(order.getTotalAmount());
                refund.setIdempotencyKey(refundKey);
                refund.setStatus("PENDING");
                refunds.save(refund);
            }
            order.setRefundStatus("PENDING");
            publisher.publishRefundRequest(new RefundRequest(id, order.getTotalAmount(), refundKey, order.getUserId()));
            order.setStatus("CANCELLED");
            orders.save(order);
            notifyOrder(order, "CANCELLED");
            return view(order);
        });
    }

    public OrderView get(Long id) { return orders.findById(id).map(this::view).orElse(null); }

    public OrderView getForUser(Long id, Long userId) {
        requireUser(userId);
        Order order = orders.findById(id).orElse(null);
        if (order == null) return null;
        if (!order.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Order does not belong to user");
        }
        return view(order);
    }

    public List<OrderView> getAllOrders(Long userId) {
        requireUser(userId);
        return orders.findByUserIdOrderByIdDesc(userId).stream().map(this::view).toList();
    }

    private OrderView view(Order order) {
        OrderView v = new OrderView(order.getId(), order.getStatus(), order.getTotalAmount(), null);
        v.setRefundStatus(order.getRefundStatus());
        v.setPaymentAttemptId(order.getPaymentAttemptId());
        v.setCheckoutId(order.getCheckoutId());
        v.setShippingAddress(order.getShippingAddress());
        v.setShippingFee(order.getShippingFee());
        v.setCurrency(order.getCurrency());
        v.setQuoteVersion(order.getQuoteVersion());
        v.setItems(items.findByOrderId(order.getId()).stream().map(item -> new OrderView.Line(
                item.getProduct().getId(), item.getQty(), item.getPriceAtOrder(), item.getWarehouseStockId())).toList());
        return v;
    }

    private void notifyOrder(Order order, String type) {
        var content = EmailMessageTemplate.getOrderStatusMessage(type);
        String email = users.findById(order.getUserId()).map(User::getEmail).orElse("customer@example.com");
        publisher.publishEmailNotification(new EmailNotification(order.getId(), type, email, content.subject(), content.body()));
    }

    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("X-User-Id is required");
        }
    }
}
