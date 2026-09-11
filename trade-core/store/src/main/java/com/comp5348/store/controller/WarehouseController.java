package com.comp5348.store.controller;

import com.comp5348.store.dto.StockView;
import com.comp5348.store.dto.WarehouseView;
import com.comp5348.store.model.Warehouse;
import com.comp5348.store.service.WarehouseCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseCatalogService catalog;

    public WarehouseController(WarehouseCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<List<WarehouseView>> getAllWarehouses() {
        return ResponseEntity.ok(catalog.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseView> getWarehouse(@PathVariable Long id) {
        WarehouseView warehouse = catalog.findById(id);
        return warehouse == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(warehouse);
    }

    @PostMapping
    public ResponseEntity<WarehouseView> createWarehouse(@RequestBody Warehouse warehouse) {
        return ResponseEntity.ok(catalog.create(warehouse));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WarehouseView> updateWarehouse(@PathVariable Long id, @RequestBody Warehouse warehouse) {
        WarehouseView updated = catalog.update(id, warehouse);
        return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id) {
        return catalog.delete(id) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/stocks")
    public ResponseEntity<List<StockView>> getAllStocks() {
        return ResponseEntity.ok(catalog.findAllStocks());
    }

    @GetMapping("/{warehouseId}/stocks")
    public ResponseEntity<List<StockView>> getWarehouseStocks(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(catalog.findStocks(warehouseId));
    }
}




