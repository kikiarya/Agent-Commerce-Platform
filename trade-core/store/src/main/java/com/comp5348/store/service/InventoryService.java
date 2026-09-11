// service/InventoryService.java
package com.comp5348.store.service;

import com.comp5348.store.dto.WarehouseAllocation;
import com.comp5348.store.exception.InsufficientStockException;
import com.comp5348.store.model.WarehouseStock;
import com.comp5348.store.repository.WarehouseStockRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final WarehouseStockRepository stocks;
    private final CacheInvalidationService invalidation;
    public InventoryService(WarehouseStockRepository stocks, CacheInvalidationService invalidation) {
        this.stocks = stocks;
        this.invalidation = invalidation;
    }

    public WarehouseStock getStock(Long id) { return stocks.findById(id).orElseThrow(); }

    /** Single warehouse allocation (preferred attempt) */
    @Transactional
    public WarehouseStock reserveSingle(Long productId, int qty) {
        WarehouseStock s = stocks
                .findFirstByProductIdAndQuantityGreaterThanEqualOrderByQuantityDesc(productId, qty)
                .orElseThrow(() -> new InsufficientStockException(productId, getTotalAvailable(productId), qty));
        s.setQuantity(s.getQuantity() - qty);
        WarehouseStock saved = stocks.save(s);
        invalidation.stockChanged();
        return saved;
    }

    /**
     * Smart inventory allocation: prefer single warehouse, automatically combine multiple warehouses when insufficient
     */
    @Transactional
    public List<WarehouseAllocation> reserveStock(Long productId, int qty) {
        log.info("Attempting to reserve {} units of product {}", qty, productId);

        // Check if total stock is sufficient
        int totalAvailable = getTotalAvailable(productId);
        if (totalAvailable < qty) {
            log.error("Insufficient total stock: need {}, available {}", qty, totalAvailable);
            throw new InsufficientStockException(productId, totalAvailable, qty);
        }

        // Prefer single warehouse fulfillment
        WarehouseStock single = stocks
                .findFirstByProductIdAndQuantityGreaterThanEqualOrderByQuantityDesc(productId, qty)
                .orElse(null);

        if (single != null) {
            log.info("Single warehouse fulfillment possible: {} (available: {})",
                    single.getWarehouse().getName(), single.getQuantity());
            single.setQuantity(single.getQuantity() - qty);
            stocks.save(single);
            invalidation.stockChanged();

            List<WarehouseAllocation> allocations = new ArrayList<>();
            allocations.add(new WarehouseAllocation(
                    single.getWarehouse().getId(),
                    single.getId(),
                    single.getWarehouse().getName(),
                    qty
            ));
            return allocations;
        }

        // Single warehouse insufficient -> enable multi-warehouse allocation
        log.info("Single warehouse insufficient, attempting multiple warehouse allocation...");
        return reserveFromMultiple(productId, qty);
    }

    /**
     * Multi-warehouse combination allocation (greedy algorithm)
     */
    @Transactional
    public List<WarehouseAllocation> reserveFromMultiple(Long productId, int qty) {
        // Get all warehouses with stock, sorted by quantity in descending order
        List<WarehouseStock> availableStocks =
                stocks.findByProductIdAndQuantityGreaterThanOrderByQuantityDesc(productId, 0);

        if (availableStocks.isEmpty()) {
            throw new InsufficientStockException(productId, 0, qty);
        }

        int totalAvailable = availableStocks.stream()
                .mapToInt(WarehouseStock::getQuantity)
                .sum();

        if (totalAvailable < qty) {
            throw new InsufficientStockException(productId, totalAvailable, qty);
        }

        // Greedy allocation
        List<WarehouseAllocation> allocations = new ArrayList<>();
        int remaining = qty;

        for (WarehouseStock stock : availableStocks) {
            if (remaining <= 0) break;

            int allocateQty = Math.min(stock.getQuantity(), remaining);
            stock.setQuantity(stock.getQuantity() - allocateQty);
            stocks.save(stock);

            allocations.add(new WarehouseAllocation(
                    stock.getWarehouse().getId(),
                    stock.getId(),
                    stock.getWarehouse().getName(),
                    allocateQty
            ));

            log.info("Allocated {} units from warehouse: {} (remaining: {})",
                    allocateQty, stock.getWarehouse().getName(), remaining - allocateQty);

            remaining -= allocateQty;
        }

        if (remaining > 0) {
            throw new InsufficientStockException(productId, totalAvailable - remaining, qty);
        }

        log.info("Multiple warehouse allocation successful: {} warehouses used", allocations.size());
        invalidation.stockChanged();
        return allocations;
    }

    /** Get total available stock */
    public int getTotalAvailable(Long productId) {
        return stocks.findByProductId(productId).stream()
                .mapToInt(WarehouseStock::getQuantity)
                .sum();
    }

    /** Rollback inventory when order is cancelled */
    @Transactional
    public void restockMultiple(List<WarehouseAllocation> allocations) {
        log.info("Restocking {} warehouse allocations", allocations.size());
        for (WarehouseAllocation allocation : allocations) {
            WarehouseStock stock = stocks.findById(allocation.warehouseStockId())
                    .orElseThrow(() -> new IllegalStateException("Warehouse stock not found: " + allocation.warehouseStockId()));
            stock.setQuantity(stock.getQuantity() + allocation.quantity());
            stocks.save(stock);
            log.info("Restocked {} units to warehouse: {} (new quantity: {})",
                    allocation.quantity(), allocation.warehouseName(), stock.getQuantity());
        }
        invalidation.stockChanged();
    }

    @Transactional
    public void restock(Long stockId, int qty) {
        WarehouseStock s = stocks.findById(stockId).orElseThrow();
        s.setQuantity(s.getQuantity() + qty);
        stocks.save(s);
        invalidation.stockChanged();
    }


        /** Get all stock records */
    public List<WarehouseStock> findAllStocks() {
        return stocks.findAll();
    }

    /** Get stock distribution for a specific product */
    public List<WarehouseStock> findStocksByProduct(Long productId) {
        return stocks.findByProductId(productId);
    }

}
