package com.comp5348.store.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
public class RedisCacheConfig implements CachingConfigurer {
    public static final String PRODUCTS = "products";
    public static final String PRODUCT = "product";
    public static final String WAREHOUSES = "warehouses";
    public static final String WAREHOUSE = "warehouse";
    public static final String STOCKS = "stocks";
    public static final String WAREHOUSE_STOCKS = "warehouseStocks";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        var serializer = new GenericJackson2JsonRedisSerializer();
        var defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith("ecommerce:v1:")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .entryTtl(Duration.ofMinutes(10));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        PRODUCTS, defaults,
                        PRODUCT, defaults,
                        WAREHOUSES, defaults,
                        WAREHOUSE, defaults,
                        STOCKS, defaults.entryTtl(Duration.ofSeconds(15)),
                        WAREHOUSE_STOCKS, defaults.entryTtl(Duration.ofSeconds(15))))
                .enableStatistics()
                .build();
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private static final class LoggingCacheErrorHandler implements CacheErrorHandler {
        private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.warn("Redis cache read failed for {}:{}; falling back to PostgreSQL", cache.getName(), key, exception);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.warn("Redis cache write failed for {}:{}", cache.getName(), key, exception);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.warn("Redis cache eviction failed for {}:{}", cache.getName(), key, exception);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.warn("Redis cache clear failed for {}", cache.getName(), exception);
        }
    }
}
