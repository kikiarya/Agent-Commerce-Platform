package com.comp5348.store.dto;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
public record ProductView(Long id, String sku, String name, BigDecimal price,
                          Map<String,String> attributes, Set<String> purposes) {
    public ProductView(Long id, String sku, String name, BigDecimal price) {
        this(id,sku,name,price,Map.of(),Set.of());
    }
}
