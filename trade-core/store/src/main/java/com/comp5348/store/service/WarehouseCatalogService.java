package com.comp5348.store.service;

import com.comp5348.store.config.RedisCacheConfig;
import com.comp5348.store.dto.StockView;
import com.comp5348.store.dto.WarehouseView;
import com.comp5348.store.model.Warehouse;
import com.comp5348.store.model.WarehouseStock;
import com.comp5348.store.repository.WarehouseRepository;
import com.comp5348.store.repository.WarehouseStockRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WarehouseCatalogService {
    private final WarehouseRepository warehouses;
    private final WarehouseStockRepository stocks;
    private final CacheInvalidationService invalidation;

    public WarehouseCatalogService(WarehouseRepository warehouses, WarehouseStockRepository stocks,
                                   CacheInvalidationService invalidation) {
        this.warehouses = warehouses;
        this.stocks = stocks;
        this.invalidation = invalidation;
    }

    @Cacheable(cacheNames = RedisCacheConfig.WAREHOUSES, key = "'all'", sync = true)
    public List<WarehouseView> findAll() {
        return warehouses.findAll().stream().map(this::toView).toList();
    }

    @Cacheable(cacheNames = RedisCacheConfig.WAREHOUSE, key = "#id", unless = "#result == null", sync = true)
    public WarehouseView findById(Long id) {
        return warehouses.findById(id).map(this::toView).orElse(null);
    }

    @Cacheable(cacheNames = RedisCacheConfig.STOCKS, key = "'all'", sync = true)
    public List<StockView> findAllStocks() {
        return stocks.findAll().stream().map(this::toStockView).toList();
    }

    @Cacheable(cacheNames = RedisCacheConfig.WAREHOUSE_STOCKS, key = "#warehouseId", sync = true)
    public List<StockView> findStocks(Long warehouseId) {
        return stocks.findByWarehouseId(warehouseId).stream().map(this::toStockView).toList();
    }

    @Transactional
    public WarehouseView create(Warehouse warehouse) {
        Warehouse saved = warehouses.save(warehouse);
        invalidation.warehouseChanged(saved.getId());
        return toView(saved);
    }

    @Transactional
    public WarehouseView update(Long id, Warehouse warehouse) {
        Warehouse existing = warehouses.findById(id).orElse(null);
        if (existing == null) return null;
        existing.setName(warehouse.getName());
        existing.setLocation(warehouse.getLocation());
        Warehouse saved = warehouses.save(existing);
        invalidation.warehouseChanged(id);
        return toView(saved);
    }

    @Transactional
    public boolean delete(Long id) {
        if (!warehouses.existsById(id)) return false;
        warehouses.deleteById(id);
        invalidation.warehouseChanged(id);
        return true;
    }

    private WarehouseView toView(Warehouse warehouse) {
        return new WarehouseView(warehouse.getId(), warehouse.getName(), warehouse.getLocation());
    }

    private StockView toStockView(WarehouseStock stock) {
        return new StockView(stock.getId(), stock.getWarehouse().getId(), stock.getWarehouse().getName(),
                stock.getProduct().getId(), stock.getProduct().getName(), stock.getQuantity());
    }
}
