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

    public CheckoutService(CheckoutSessionRepository sessions, CheckoutLineRepository lines,
                           ProductRepository products, OrderService orders) {
        this.sessions = sessions;
        this.lines = lines;
        this.products = products;
        this.orders = orders;
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
    public CheckoutView confirm(Long userId, Long checkoutId) {
        CheckoutSession session = lockedOwned(userId, checkoutId);
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

    @Transactional
    public OrderView complete(Long userId, Long checkoutId, CompleteCheckoutRequest req) {
        requireUser(userId);
        CheckoutSession session = lockedOwned(userId, checkoutId);
        if (!"CONFIRMED".equals(session.getStatus())) {
            throw new IllegalStateException("Checkout must be CONFIRMED before complete: " + session.getStatus());
        }
        assertNotExpired(session);
        if (session.getOrderId() != null) {
            return orders.getForUser(session.getOrderId(), userId);
        }
        List<CheckoutLine> current = lines.findByCheckoutId(checkoutId);
        if (current.isEmpty()) {
            throw new IllegalArgumentException("Checkout has no items");
        }
        // V1 stage-1 complete: single-line only (multi-line in stage 2)
        if (current.size() != 1) {
            throw new IllegalArgumentException("V1 stage-1 supports single-SKU checkout only; got " + current.size() + " lines");
        }
        CheckoutLine line = current.get(0);
        OrderView order = orders.placeFromCheckout(
                userId,
                line.getProductId(),
                line.getQuantity(),
                session.getTotalAmount(),
                req.idempotencyKey(),
                req.bankMock(),
                checkoutId
        );
        session.setOrderId(order.getOrderId());
        session.setStatus("COMPLETED");
        sessions.save(session);
        return order;
    }

    @Transactional(readOnly = true)
    public CheckoutView get(Long userId, Long checkoutId) {
        return view(lockedOwned(userId, checkoutId));
    }

    private void replaceLines(CheckoutSession session, List<CreateCheckoutRequest.CheckoutItemRequest> items) {
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
            throw new IllegalStateException("Checkout does not belong to user");
        }
        if ("COMPLETED".equals(session.getStatus())) {
            return session;
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())
                && !"COMPLETED".equals(session.getStatus())) {
            session.setStatus("EXPIRED");
            sessions.save(session);
            throw new IllegalStateException("Checkout expired");
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
            session.setStatus("EXPIRED");
            sessions.save(session);
            throw new IllegalStateException("Checkout expired");
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
                session.getStatus(),
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
