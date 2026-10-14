package com.example.cart;

public interface DiscountService {

    /**
     * Zwraca procentowy rabat dla klienta (0.0 - 1.0).
     * Np. 0.1 oznacza 10% rabatu.
     */
    double getDiscountForCustomer(String customerId);
}