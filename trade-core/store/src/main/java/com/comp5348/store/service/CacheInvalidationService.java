package com.comp5348.store.service;

import com.comp5348.store.config.RedisCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CacheInvalidationService {
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);
    private final CacheManager cacheManager;

    public CacheInvalidationService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void productChanged(Long productId) {
        afterCommit(() -> {
            clear(RedisCacheConfig.PRODUCTS);
            evict(RedisCacheConfig.PRODUCT, productId);
        });
    }

    public void warehouseChanged(Long warehouseId) {
        afterCommit(() -> {
            clear(RedisCacheConfig.WAREHOUSES);
            clear(RedisCacheConfig.STOCKS);
            clear(RedisCacheConfig.WAREHOUSE_STOCKS);
            evict(RedisCacheConfig.WAREHOUSE, warehouseId);
        });
    }

    public void stockChanged() {
        afterCommit(() -> {
            clear(RedisCacheConfig.STOCKS);
            clear(RedisCacheConfig.WAREHOUSE_STOCKS);
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void evict(String cacheName, Object key) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.evict(key);
        } catch (RuntimeException exception) {
            log.warn("Could not evict cache {}:{}", cacheName, key, exception);
        }
    }

    private void clear(String cacheName) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        } catch (RuntimeException exception) {
            log.warn("Could not clear cache {}", cacheName, exception);
        }
    }
}
