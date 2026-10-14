package com.example.cart;

import java.util.List;

public class ShoppingCartService {

    private final ProductRepository productRepository;
    private final DiscountService discountService;

    public ShoppingCartService(ProductRepository productRepository,
                               DiscountService discountService) {
        this.productRepository = productRepository;
        this.discountService = discountService;
    }

    /**
     * Oblicza końcową cenę koszyka po rabacie.
     */
    public double calculateTotal(String customerId, List<CartItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Koszyk nie może być pusty");
        }

        double subtotal = 0.0;
        for (CartItem item : items) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
            subtotal += product.getPrice() * item.getQuantity();
        }

        double discount = discountService.getDiscountForCustomer(customerId);
        return subtotal * (1.0 - discount);
    }
}