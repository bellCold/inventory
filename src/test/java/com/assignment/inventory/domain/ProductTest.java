package com.assignment.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    @DisplayName("신규 상품은 재고 0 으로 등록된다")
    void register() {
        Product product = Product.register("상품 A");

        assertThat(product.getName()).isEqualTo("상품 A");
        assertThat(product.getQuantity()).isZero();
    }

    @Nested
    @DisplayName("입고")
    class Increase {

        @Test
        @DisplayName("입고한 수량만큼 재고가 증가한다")
        void increase() {
            Product product = productWithStock(10);

            product.increase(5);

            assertThat(product.getQuantity()).isEqualTo(15);
        }

        @Test
        @DisplayName("여러 번 입고하면 수량이 누적된다")
        void increaseMultipleTimes() {
            Product product = productWithStock(0);

            product.increase(3);
            product.increase(7);

            assertThat(product.getQuantity()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("출고")
    class Decrease {

        @Test
        @DisplayName("출고한 수량만큼 재고가 감소한다")
        void decrease() {
            Product product = productWithStock(10);

            product.decrease(3);

            assertThat(product.getQuantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("재고 전량을 출고하면 재고는 0 이 된다")
        void decreaseAll() {
            Product product = productWithStock(10);

            product.decrease(10);

            assertThat(product.getQuantity()).isZero();
        }

        @Test
        @DisplayName("재고보다 많이 출고하면 예외가 발생하고 재고는 변하지 않는다")
        void decreaseMoreThanStock() {
            Product product = productWithStock(10);

            assertThatThrownBy(() -> product.decrease(11))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessage("재고가 부족합니다. (현재 재고: 10, 요청 수량: 11)");
            assertThat(product.getQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("재고가 0 이면 1개도 출고할 수 없다")
        void decreaseFromEmpty() {
            Product product = productWithStock(0);

            assertThatThrownBy(() -> product.decrease(1)).isInstanceOf(InsufficientStockException.class);
            assertThat(product.getQuantity()).isZero();
        }
    }

    private Product productWithStock(long quantity) {
        Product product = Product.register("상품 A");
        product.increase(quantity);
        return product;
    }
}
