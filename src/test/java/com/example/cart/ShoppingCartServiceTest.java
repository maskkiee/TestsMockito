package com.example.cart;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ShoppingCartService - testy mockowania")
@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceTest {

    //stałe
    private static final double VIP_DISCOUNT = 0.1;
    private static final double TOLERANCE = 0.01;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DiscountService discountService;

    @InjectMocks
    private ShoppingCartService shoppingCartService;

    @Nested
    @DisplayName("calculateTotal()")
    class CalculateTotalTests {

        @Test
        void priceForOneProductNoDiscount(){
            //given
            when(productRepository.findById("product-01"))
                    .thenReturn(Optional.of(new Product("product-01", "Książka", 50)));
            when(discountService.getDiscountForCustomer("customer-01")).thenReturn(0.0);

            //when
            double total = shoppingCartService.calculateTotal("customer-01",
                    List.of(new CartItem("product-01", 2)));

            //then
            assertThat(total).isCloseTo(100.0, within(TOLERANCE));
        }

        @Test
        void priceForOneProductVIPDiscount(){
            //given
            when(productRepository.findById("product-01"))
                    .thenReturn(Optional.of(new Product("product-01", "Książka", 50)));
            when(discountService.getDiscountForCustomer("vip-01")).thenReturn(VIP_DISCOUNT);

            //when
            double total = shoppingCartService.calculateTotal("vip-01",
                    List.of(new CartItem("product-01", 2)));

            //then
            assertThat(total).isCloseTo(90.0, within(TOLERANCE));
        }

        @Test
        void priceForMultipleProducts(){
            //given
            when(productRepository.findById("product-01"))
                    .thenReturn(Optional.of(new Product("product-01", "Książka", 50)));
            when(productRepository.findById("product-02"))
                    .thenReturn(Optional.of(new Product("product-02", "Płyta", 75)));
            when(discountService.getDiscountForCustomer("customer-01")).thenReturn(0.0);

            //when
            double total = shoppingCartService.calculateTotal("customer-01",
                    List.of(new CartItem("product-01", 2),
                            new CartItem("product-02", 3)));

            //then
            assertThat(total).isCloseTo(325.00, within(TOLERANCE));
        }

        @Test
        void cartIsEmpty(){
            assertThatThrownBy(() -> shoppingCartService.calculateTotal("customer-01", List.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Koszyk nie może być pusty");

            verifyNoInteractions(productRepository, discountService);
        }

        @Test
        void productDoesNotExist(){
            //given
            when(productRepository.findById("product-01"))
                    .thenReturn(Optional.empty());

            //when + then
            assertThatThrownBy(() -> shoppingCartService.calculateTotal("customer-01",
                    List.of(new CartItem("product-01", 3))))
                    .isInstanceOf(ProductNotFoundException.class).hasMessage("Produkt nie znaleziony: product-01");
        }
    }
}
