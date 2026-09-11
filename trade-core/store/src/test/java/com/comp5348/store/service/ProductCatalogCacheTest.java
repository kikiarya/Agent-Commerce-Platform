package com.comp5348.store.service;

import com.comp5348.store.config.RedisCacheConfig;
import com.comp5348.store.model.Product;
import com.comp5348.store.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProductCatalogCacheTest.TestConfig.class)
class ProductCatalogCacheTest {
    @Autowired ProductCatalogService catalog;
    @Autowired ProductRepository repository;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        reset(repository);
        cacheManager.getCache(RedisCacheConfig.PRODUCTS).clear();
        cacheManager.getCache(RedisCacheConfig.PRODUCT).clear();
    }

    @Test
    void repeatedCatalogReadUsesCache() {
        Product product = new Product("BOARD-1", "City Board", new BigDecimal("129.00"));
        product.setId(1L);
        when(repository.findAll()).thenReturn(List.of(product));

        assertThat(catalog.findAll()).hasSize(1);
        assertThat(catalog.findAll()).hasSize(1);

        verify(repository, times(1)).findAll();
    }

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(RedisCacheConfig.PRODUCTS, RedisCacheConfig.PRODUCT);
        }

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        CacheInvalidationService cacheInvalidationService() {
            return mock(CacheInvalidationService.class);
        }

        @Bean
        InventoryService inventoryService() {
            return mock(InventoryService.class);
        }

        @Bean
        ProductCatalogService productCatalogService(ProductRepository repository,
                                                    CacheInvalidationService invalidation,
                                                    InventoryService inventory) {
            return new ProductCatalogService(repository, invalidation, inventory);
        }
    }
}
