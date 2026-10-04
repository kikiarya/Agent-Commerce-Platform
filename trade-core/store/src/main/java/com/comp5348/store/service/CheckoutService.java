package com.comp5348.store.service;

import com.comp5348.store.dto.*;
import com.comp5348.store.model.CheckoutLine;
import com.comp5348.store.model.CheckoutSession;
import com.comp5348.store.model.Product;
import com.comp5348.store.repository.CheckoutLineRepository;
import com.comp5348.store.repository.CheckoutSessionRepository;
import com.comp5348.store.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.comp5348.store.exception.CheckoutConflictException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class CheckoutService {

    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("99");
    public static final BigDecimal SHIPPING_FEE = new BigDecimal("10");
    public static final long QUOTE_TTL_MINUTES = 30;

    private final CheckoutSessionRepository sessions;
    private final CheckoutLineRepository lines;
    private final ProductRepository products;
    private final OrderService orders;
    private final TransactionTemplate tx;

    public CheckoutService(CheckoutSessionRepository sessions, CheckoutLineRepository lines,
                           ProductRepository products, OrderService orders, PlatformTransactionManager manager) {
        this.sessions = sessions;
        this.lines = lines;
        this.products = products;
        this.orders = orders;
        this.tx = new TransactionTemplate(manager);
    }

    @Transactional
    public CheckoutView create(Long userId, CreateCheckoutRequest req) {
        requireUser(userId);
        CheckoutSession session = new CheckoutSession();
        session.setUserId(userId);
        session.setStatus("DRAFT");
        session.setShippingAddress(blankToNull(req.shippingAddress()));
        sessions.save(session);
        replaceLines(session, req.items());
        return view(session);
    }

    @Transactional
    public CheckoutView update(Long userId, Long checkoutId, UpdateCheckoutRequest req) {
        CheckoutSession session = lockedOwned(userId, checkoutId);
        assertMutable(session);
        if (req.shippingAddress() != null) {
            session.setShippingAddress(blankToNull(req.shippingAddress()));
        }
        if (req.items() != null && !req.items().isEmpty()) {
            replaceLines(session, req.items());
        }
        // Any mutation clears confirmation and requires re-quote
        session.setStatus("DRAFT");
        session.setConfirmedAt(null);
        session.setQuotedAt(null);
        session.setExpiresAt(null);
        session.setSubtotal(null);
        session.setShippingFee(null);
        session.setTotalAmount(null);
        sessions.save(session);
        return view(session);
    }

    @Transactional
    public CheckoutView quote(Long userId, Long checkoutId) {
        CheckoutSession session = lockedOwned(userId, checkoutId);
        assertMutable(session);
        List<CheckoutLine> current = lines.findByCheckoutId(checkoutId);
        if (current.isEmpty()) {
            throw new IllegalArgumentException("Checkout has no items");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        List<CheckoutLine> refreshed = new ArrayList<>();
        for (CheckoutLine line : current) {
            Product product = products.findById(line.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + line.getProductId()));
            line.setUnitPrice(product.getPrice());
            lines.save(line);
            refreshed.add(line);
            subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        }
        BigDecimal shipping = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) < 0 ? SHIPPING_FEE : BigDecimal.ZERO;
        Instant now = Instant.now();
        session.setSubtotal(subtotal);
        session.setShippingFee(shipping);
        session.setTotalAmount(subtotal.add(shipping));
        session.setCurrency("CNY");
        session.setQuoteVersion(session.getQuoteVersion() + 1);
        session.setStatus("QUOTED");
        session.setQuotedAt(now);
        session.setConfirmedAt(null);
        session.setExpiresAt(now.plus(QUOTE_TTL_MINUTES, ChronoUnit.MINUTES));
        sessions.save(session);
        return view(session, refreshed);
    }

    @Transactional
    public CheckoutView confirm(Long userId, Long checkoutId, int quoteVersion) {
        CheckoutSession session = lockedOwned(userId, checkoutId);
        requireVersion(session, quoteVersion);
        if (!"QUOTED".equals(session.getStatus()) && !"CONFIRMED".equals(session.getStatus())) {
            throw new IllegalStateException("Checkout must be QUOTED before confirm: " + session.getStatus());
        }
        assertNotExpired(session);
        if (session.getShippingAddress() == null || session.getShippingAddress().isBlank()) {
            throw new IllegalArgumentException("Shipping address is required before confirm");
        }
        session.setStatus("CONFIRMED");
        session.setConfirmedAt(Instant.now());
        sessions.save(session);
        return view(session);
    }

    public OrderView complete(Long userId, Long checkoutId, CompleteCheckoutRequest req) {
        // Persist inventory, order, payment intent and checkout link BEFORE any remote debit.
        OrderView pending = tx.execute(status -> completeLocked(userId, checkoutId, req));
        try {
            orders.settlePayment(pending.getOrderId());
        } catch (RuntimeException error) {
            // The durable PAYMENT_PENDING intent is recovered by OrderService's scheduler.
            org.slf4j.LoggerFactory.getLogger(CheckoutService.class)
                    .warn("Checkout payment deferred for order {}", pending.getOrderId());
        }
        return orders.getForUser(pending.getOrderId(), userId);
    }

    private OrderView completeLocked(Long userId, Long checkoutId, CompleteCheckoutRequest req) {
        requireUser(userId);
        if (req.idempotencyKey() == null || req.idempotencyKey().isBlank() || req.idempotencyKey().length() > 48)
            throw new IllegalArgumentException("Idempotency key must contain 1 to 48 characters");
        CheckoutSession session = lockedOwned(userId, checkoutId);
        requireVersion(session, req.quoteVersion());
        if (session.getOrderId() != null) {
            if (!req.idempotencyKey().equals(session.getCompletionKey()))
                throw new CheckoutConflictException("Checkout already completed with a different key");
            return orders.getForUser(session.getOrderId(), userId);
        }
        if (!"CONFIRMED".equals(session.getStatus())) {
            throw new IllegalStateException("Checkout must be CONFIRMED before complete: " + session.getStatus());
        }
        assertNotExpired(session);
        List<CheckoutLine> current = lines.findByCheckoutId(checkoutId);
        if (current.isEmpty()) {
            throw new IllegalArgumentException("Checkout has no items");
        }
        for (CheckoutLine line : current.stream()
                .sorted(java.util.Comparator.comparing(CheckoutLine::getProductId)).toList()) {
            Product product = products.findLockedById(line.getProductId())
                    .orElseThrow(() -> new CheckoutConflictException("Product no longer available; refresh quote"));
            if (product.getPrice().compareTo(line.getUnitPrice()) != 0)
                throw new CheckoutConflictException("Price changed; refresh quote and confirm again");
        }
        OrderView order = orders.reserveConfirmedCheckout(session, current, req.idempotencyKey(), req.bankMock());
        session.setOrderId(order.getOrderId());
        session.setCompletionKey(req.idempotencyKey());
        session.setStatus("COMPLETED");
        sessions.save(session);
        return order;
    }

    @Transactional(readOnly = true)
    public CheckoutView get(Long userId, Long checkoutId) {
        requireUser(userId);
        CheckoutSession session = sessions.findById(checkoutId)
                .orElseThrow(() -> new IllegalArgumentException("Checkout not found"));
        if (!session.getUserId().equals(userId)) throw new AccessDeniedException("Checkout does not belong to user");
        return view(session);
    }

    private void replaceLines(CheckoutSession session, List<CreateCheckoutRequest.CheckoutItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > 100)
            throw new IllegalArgumentException("Checkout requires 1 to 100 items");
        var seen = new java.util.HashSet<Long>();
        for (var item : items) {
            if (item == null || item.skuId() == null || item.quantity() <= 0 || !seen.add(item.skuId()))
                throw new IllegalArgumentException("Items must have positive quantities and distinct SKU IDs");
        }
        lines.deleteByCheckoutId(session.getId());
        for (var item : items) {
            Product product = products.findById(item.skuId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.skuId()));
            CheckoutLine line = new CheckoutLine();
            line.setCheckout(session);
            line.setProductId(product.getId());
            line.setQuantity(item.quantity());
            line.setUnitPrice(product.getPrice());
            lines.save(line);
        }
    }

    private CheckoutSession lockedOwned(Long userId, Long checkoutId) {
        requireUser(userId);
        CheckoutSession session = sessions.findLockedById(checkoutId)
                .orElseThrow(() -> new IllegalArgumentException("Checkout not found: " + checkoutId));
        if (!session.getUserId().equals(userId)) {
            throw new AccessDeniedException("Checkout does not belong to user");
        }
        if ("COMPLETED".equals(session.getStatus())) {
            return session;
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())
                && !"COMPLETED".equals(session.getStatus())) {
            throw new CheckoutConflictException("Checkout expired; create a new session");
        }
        return session;
    }

    private void assertMutable(CheckoutSession session) {
        if ("COMPLETED".equals(session.getStatus()) || "EXPIRED".equals(session.getStatus())) {
            throw new IllegalStateException("Checkout is not mutable: " + session.getStatus());
        }
    }

    private void assertNotExpired(CheckoutSession session) {
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
            throw new CheckoutConflictException("Checkout expired; create a new session");
        }
    }

    private CheckoutView view(CheckoutSession session) {
        return view(session, lines.findByCheckoutId(session.getId()));
    }

    private CheckoutView view(CheckoutSession session, List<CheckoutLine> current) {
        List<CheckoutView.Line> itemViews = current.stream()
                .map(l -> new CheckoutView.Line(l.getProductId(), l.getQuantity(), l.getUnitPrice()))
                .toList();
        return new CheckoutView(
                session.getId(),
                session.getUserId(),
                session.getOrderId() == null && session.getExpiresAt() != null
                        && session.getExpiresAt().isBefore(Instant.now()) ? "EXPIRED" : session.getStatus(),
                session.getQuoteVersion(),
                session.getSubtotal(),
                session.getShippingFee(),
                session.getTotalAmount(),
                session.getCurrency(),
                session.getShippingAddress(),
                session.getOrderId(),
                session.getExpiresAt(),
                itemViews
        );
    }

    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("X-User-Id is required");
        }
    }

    private static void requireVersion(CheckoutSession session, int version) {
        if (version < 1 || version != session.getQuoteVersion())
            throw new CheckoutConflictException("Quote version changed; refresh and confirm the displayed quote");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
