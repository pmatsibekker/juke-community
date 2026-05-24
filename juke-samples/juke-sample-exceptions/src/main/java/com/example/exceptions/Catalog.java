package com.example.exceptions;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The (tiny) product catalog. Plain in-process data — it is not a Juke seam;
 * the only seam in this sample is the OMS ({@link IOrderManagementSystem}).
 */
@Service
public class Catalog {

    private static final List<Product> PRODUCTS = List.of(
            new Product("SKU-LYRE-01", "Superb Lyrebird Figurine", 4200),
            new Product("SKU-VINYL-7", "Juke 7\" Vinyl Single",     1900),
            new Product("SKU-MUG-42",  "Record-and-Replay Mug",      1500));

    public List<Product> products() {
        return PRODUCTS;
    }

    Product find(String sku) {
        return PRODUCTS.stream()
                .filter(p -> p.sku().equals(sku))
                .findFirst()
                .orElse(PRODUCTS.get(0));
    }
}
