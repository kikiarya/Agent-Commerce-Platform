package com.comp5348.store.controller;

import com.comp5348.store.dto.ProductView;
import com.comp5348.store.model.Product;
import com.comp5348.store.service.ProductCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductCatalogService catalog;

    public ProductController(ProductCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<List<ProductView>> getAllProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Boolean inStock) {
        if (q != null || priceMax != null || inStock != null) {
            return ResponseEntity.ok(catalog.search(q, priceMax, inStock));
        }
        return ResponseEntity.ok(catalog.findAll());
    }

    /** Explicit search alias for Agent tool search_products */
    @GetMapping("/search")
    public ResponseEntity<List<ProductView>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Boolean inStock) {
        return ResponseEntity.ok(catalog.search(q, priceMax, inStock));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductView> getProduct(@PathVariable Long id) {
        ProductView product = catalog.findById(id);
        return product == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(product);
    }

    @PostMapping
    public ResponseEntity<ProductView> createProduct(@RequestBody Product product) {
        return ResponseEntity.ok(catalog.create(product));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductView> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        ProductView updated = catalog.update(id, product);
        return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        return catalog.delete(id) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}




