package com.example.exceptions;

import org.juke.framework.annotation.JukeController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for the SPA.
 *
 * <ul>
 *   <li>{@code GET  /api/products} — the catalog the shopper picks from</li>
 *   <li>{@code POST /api/order}    — place one order; delegates to the
 *       {@code @Juke}-mediated {@link OrderService}</li>
 * </ul>
 *
 * <p>The {@code /service/*} control surface (record/replay/session/remix/
 * coverage) is contributed by the Remix and coverage modules on the same origin.
 */
@JukeController
@RestController
@RequestMapping("/api")
public class OrderController {

    @Autowired
    private Catalog catalog;

    @Autowired
    private OrderService orderService;

    @GetMapping("/products")
    public List<Product> products() {
        return catalog.products();
    }

    @PostMapping("/order")
    public OrderResult order(@RequestBody ApiOrderRequest request) {
        return orderService.placeOrder(request.sku(), request.quantity(), request.confirmationNumber());
    }
}
