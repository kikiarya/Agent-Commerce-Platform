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
        validateMetadata(product);
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
        validateMetadata(product);
        existing.setPrice(product.getPrice());
        existing.getAttributes().clear(); existing.getAttributes().putAll(product.getAttributes());
        existing.getPurposes().clear(); existing.getPurposes().addAll(product.getPurposes());
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

    private void validateMetadata(Product p) {
        if (p.getAttributes().size() > 30 || p.getPurposes().size() > 20
            || p.getAttributes().entrySet().stream().anyMatch(e -> e.getKey() == null || e.getKey().isBlank() || e.getKey().length()>80 || e.getValue()==null || e.getValue().isBlank() || e.getValue().length()>200)
            || p.getPurposes().stream().anyMatch(v -> v==null || v.isBlank() || v.length()>80))
            throw new IllegalArgumentException("Invalid product attributes or purposes");
    }

    private ProductView toView(Product product) {
        return new ProductView(product.getId(), product.getSku(), product.getName(), product.getPrice(), java.util.Map.copyOf(product.getAttributes()), java.util.Set.copyOf(product.getPurposes()));
    }
}
