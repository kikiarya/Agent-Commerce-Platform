package com.comp5348.store.util;

import com.comp5348.store.model.Product;
import com.comp5348.store.model.Warehouse;
import com.comp5348.store.model.WarehouseStock;
import com.comp5348.store.repository.ProductRepository;
import com.comp5348.store.repository.WarehouseRepository;
import com.comp5348.store.repository.WarehouseStockRepository;
import com.comp5348.store.service.AuthService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer {
    
    private final AuthService authService;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository stockRepository;

    public DataInitializer(AuthService authService, 
                          ProductRepository productRepository,
                          WarehouseRepository warehouseRepository,
                          WarehouseStockRepository stockRepository) {
        this.authService = authService;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockRepository = stockRepository;
    }

    @PostConstruct
    public void init() {
        System.out.println("=== Initializing Data ===");
        
        // Create default users if they don't exist
        if (authService.findByUsername("customer").isEmpty()) {
            var customer = authService.createUser("customer", "COMP5348", "customer@example.com", "CUSTOMER");
            System.out.println("Created customer user with ID: " + customer.getId());
        } else {
            System.out.println("Customer user already exists");
        }
        
        if (authService.findByUsername("admin").isEmpty()) {
            var admin = authService.createUser("admin", "admin123", "admin@example.com", "ADMIN");
            System.out.println("Created admin user with ID: " + admin.getId());
        } else {
            System.out.println("Admin user already exists");
        }

        // Create sample products if none exist
        if (productRepository.count() == 0) {
            Product p1 = new Product("SK8-001", "Professional Skateboard", new BigDecimal("199.99"));
            Product p2 = new Product("SK8-002", "Beginner Skateboard", new BigDecimal("79.99"));
            Product p3 = new Product("WHL-001", "Premium Wheels Set", new BigDecimal("49.99"));
            Product p4 = new Product("TRK-001", "Aluminum Trucks", new BigDecimal("89.99"));
            Product p5 = new Product("DK-001", "Maple Deck", new BigDecimal("59.99"));
            
            productRepository.save(p1);
            productRepository.save(p2);
            productRepository.save(p3);
            productRepository.save(p4);
            productRepository.save(p5);
        }

        // Create sample warehouses if none exist
        if (warehouseRepository.count() == 0) {
            Warehouse w1 = new Warehouse("Sydney Warehouse", "Sydney, NSW");
            Warehouse w2 = new Warehouse("Melbourne Warehouse", "Melbourne, VIC");
            
            warehouseRepository.save(w1);
            warehouseRepository.save(w2);
        }

        // Create sample stock if none exists
        if (stockRepository.count() == 0) {
            var products = productRepository.findAll();
            var warehouses = warehouseRepository.findAll();
            
            if (!products.isEmpty() && !warehouses.isEmpty()) {
                Warehouse w1 = warehouses.get(0);
                Warehouse w2 = warehouses.size() > 1 ? warehouses.get(1) : w1;
                
                for (Product p : products) {
                    // Stock in warehouse 1
                    WarehouseStock s1 = new WarehouseStock(w1, p, 50);
                    stockRepository.save(s1);
                    
                    // Stock in warehouse 2 (less quantity)
                    if (warehouses.size() > 1) {
                        WarehouseStock s2 = new WarehouseStock(w2, p, 30);
                        stockRepository.save(s2);
                    }
                }
            }
        }
    }
}




