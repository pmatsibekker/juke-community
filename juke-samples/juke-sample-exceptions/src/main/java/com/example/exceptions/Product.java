package com.example.exceptions;

/**
 * A buyable catalog item. Serialized as JSON for {@code GET /api/products}.
 */
public record Product(String sku, String name, int priceCents) {
}
