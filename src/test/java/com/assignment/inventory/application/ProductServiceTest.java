package com.assignment.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assignment.inventory.domain.InsufficientStockException;
import com.assignment.inventory.domain.Product;
import com.assignment.inventory.domain.ProductNotFoundException;
import com.assignment.inventory.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductServiceTest extends IntegrationTestSupport {

    @Autowired
    ProductService productService;

    @Nested
    @DisplayName("입고")
    class Inbound {

        @Test
        @DisplayName("미등록 상품이면 신규 등록 후 입고한다")
        void registerNewProduct() {
            Product product = productService.inbound("상품 A", 10);

            assertThat(product.getId()).isNotNull();
            assertThat(product.getName()).isEqualTo("상품 A");
            assertThat(stockOf(product.getId())).isEqualTo(10);
            assertThat(productCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("등록된 상품이면 새로 만들지 않고 기존 재고에 더한다")
        void increaseExistingProduct() {
            Long id = productService.inbound("상품 A", 10).getId();

            Product product = productService.inbound("상품 A", 5);

            assertThat(product.getId()).isEqualTo(id);
            assertThat(stockOf(id)).isEqualTo(15);
            assertThat(productCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("이름이 다르면 별개의 상품으로 등록된다")
        void differentNamesAreDifferentProducts() {
            Long idA = productService.inbound("상품 A", 10).getId();
            Long idB = productService.inbound("상품 B", 20).getId();

            assertThat(idA).isNotEqualTo(idB);
            assertThat(stockOf(idA)).isEqualTo(10);
            assertThat(stockOf(idB)).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("출고")
    class Outbound {

        @Test
        @DisplayName("출고한 수량만큼 재고가 감소한다")
        void decrease() {
            Long id = productService.inbound("상품 A", 10).getId();

            Product product = productService.outbound(id, 3);

            assertThat(product.getQuantity()).isEqualTo(7);
            assertThat(stockOf(id)).isEqualTo(7);
        }

        @Test
        @DisplayName("재고 전량을 출고할 수 있다")
        void decreaseAll() {
            Long id = productService.inbound("상품 A", 10).getId();

            productService.outbound(id, 10);

            assertThat(stockOf(id)).isZero();
        }

        @Test
        @DisplayName("재고가 부족하면 예외가 발생하고 DB 의 재고는 변하지 않는다")
        void insufficientStock() {
            Long id = productService.inbound("상품 A", 10).getId();

            assertThatThrownBy(() -> productService.outbound(id, 11))
                    .isInstanceOf(InsufficientStockException.class);
            assertThat(stockOf(id)).isEqualTo(10);
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 예외가 발생한다")
        void productNotFound() {
            assertThatThrownBy(() -> productService.outbound(999L, 1))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessage("상품을 찾을 수 없습니다. (id: 999)");
        }
    }

    @Nested
    @DisplayName("재고 조회")
    class GetProduct {

        @Test
        @DisplayName("입출고가 반영된 현재 재고를 조회한다")
        void currentStock() {
            Long id = productService.inbound("상품 A", 10).getId();
            productService.outbound(id, 4);

            Product product = productService.getProduct(id);

            assertThat(product.getName()).isEqualTo("상품 A");
            assertThat(product.getQuantity()).isEqualTo(6);
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 예외가 발생한다")
        void productNotFound() {
            assertThatThrownBy(() -> productService.getProduct(999L)).isInstanceOf(ProductNotFoundException.class);
        }
    }

    private long stockOf(Long id) {
        return jdbcTemplate.queryForObject("SELECT quantity FROM product WHERE id = ?", Long.class, id);
    }

    private long productCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM product", Long.class);
    }
}
