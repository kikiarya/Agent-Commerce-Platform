package com.comp5348.store.service;

import com.comp5348.store.config.RedisCacheConfig;
import com.comp5348.store.dto.ProductView;
import com.comp5348.store.model.Product;
import com.comp5348.store.repository.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class ProductCatalogService {
    private final ProductRepository products;
    private final CacheInvalidationService invalidation;
    private final InventoryService inventory;

    public ProductCatalogService(ProductRepository products, CacheInvalidationService invalidation,
                                 InventoryService inventory) {
        this.products = products;
        this.invalidation = invalidation;
        this.inventory = inventory;
    }

    @Cacheable(cacheNames = RedisCacheConfig.PRODUCTS, key = "'all'", sync = true)
    public List<ProductView> findAll() {
        return products.findAll().stream().map(this::toView).toList();
    }

    /** Structured product search for Agent / BFF (not RAG). */
    public List<ProductView> search(String q, BigDecimal priceMax, Boolean inStockOnly) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return products.findAll().stream()
                .filter(p -> needle.isEmpty()
                        || p.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || p.getSku().toLowerCase(Locale.ROOT).contains(needle))
                .filter(p -> priceMax == null || p.getPrice().compareTo(priceMax) <= 0)
                .filter(p -> inStockOnly == null || !inStockOnly || inventory.getTotalAvailable(p.getId()) > 0)
                .map(this::toView)
                .toList();
    }

    @Cacheable(cacheNames = RedisCacheConfig.PRODUCT, key = "#id", unless = "#result == null", sync = true)
    public ProductView findById(Long id) {
        return products.findById(id).map(this::toView).orElse(null);
    }

    @Transactional
    public ProductView create(Product product) {
        Product saved = products.save(product);
        invalidation.productChanged(saved.getId());
        return toView(saved);
    }

    @Transactional
    public ProductView update(Long id, Product product) {
        Product existing = products.findById(id).orElse(null);
        if (existing == null) return null;
        existing.setSku(product.getSku());
        existing.setName(product.getName());
        existing.setPrice(product.getPrice());
        Product saved = products.save(existing);
        invalidation.productChanged(id);
        return toView(saved);
    }

    @Transactional
    public boolean delete(Long id) {
        if (!products.existsById(id)) return false;
        products.deleteById(id);
        invalidation.productChanged(id);
        return true;
    }

    private ProductView toView(Product product) {
        return new ProductView(product.getId(), product.getSku(), product.getName(), product.getPrice());
    }
}
