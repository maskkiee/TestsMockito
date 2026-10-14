package com.example.cart;

import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(String productId);
}