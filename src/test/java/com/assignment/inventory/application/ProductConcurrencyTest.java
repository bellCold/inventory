package com.assignment.inventory.application;

import com.assignment.inventory.domain.InsufficientStockException;
import com.assignment.inventory.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProductConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    ProductService productService;

    @Test
    @DisplayName("재고 50개에 100건이 동시에 1개씩 출고하면 정확히 50건만 성공하고 재고는 0 이 된다")
    void concurrentOutbound() throws Exception {
        Long id = productService.inbound("상품 A", 50).getId();

        Results results = runConcurrently(100, i -> productService.outbound(id, 1));

        assertThat(results.successCount()).isEqualTo(50);
        assertThat(results.failures()).hasSize(50).allMatch(e -> e instanceof InsufficientStockException);
        assertThat(stockOf(id)).isZero();
    }

    @Test
    @DisplayName("서로 다른 수량을 동시에 출고해도 재고는 음수가 되지 않고, 성공한 출고량만큼만 감소한다")
    void concurrentOutboundWithVariousAmounts() throws Exception {
        Long id = productService.inbound("상품 A", 100).getId();
        // 1~5 개씩 60건 → 총 요청량 180 개로 재고 100 개를 초과
        long[] amounts = IntStream.range(0, 60).mapToLong(i -> i % 5 + 1).toArray();

        Results results = runConcurrently(amounts.length, i -> productService.outbound(id, amounts[i]));

        long released = results.successIndexes().stream().mapToLong(i -> amounts[i]).sum();
        assertThat(stockOf(id)).isNotNegative().isEqualTo(100 - released);
        assertThat(results.failures()).allMatch(e -> e instanceof InsufficientStockException);
    }

    @Test
    @DisplayName("입고와 출고가 동시에 섞여 들어와도 갱신 손실 없이 최종 재고가 정확하다")
    void concurrentInboundAndOutbound() throws Exception {
        Long id = productService.inbound("상품 A", 100).getId();

        // 짝수: 3개 입고 50건(+150), 홀수: 2개 출고 50건(-100)
        Results results = runConcurrently(100, i -> {
            if (i % 2 == 0) {
                productService.inbound("상품 A", 3);
            } else {
                productService.outbound(id, 2);
            }
        });

        assertThat(results.failures()).isEmpty();
        assertThat(stockOf(id)).isEqualTo(100 + 150 - 100);
    }

    @Test
    @DisplayName("미등록 상품에 동시 입고가 몰리면 상품은 1개만 등록되고, 나머지는 충돌로 실패하며 성공한 수량만 반영된다")
    void concurrentInboundForNewProduct() throws Exception {
        Results results = runConcurrently(50, i -> productService.inbound("신상품", 2));

        assertThat(results.successCount()).isPositive();
        assertThat(results.failures()).allMatch(e -> e instanceof DataIntegrityViolationException);
        assertThat(productCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM product", Long.class)).isEqualTo(results.successCount() * 2L);
    }

    /**
     * taskCount 개의 작업을 동시에 실행하고, 작업 번호별 성공/실패 결과를 모아 반환한다.
     */
    private Results runConcurrently(int taskCount, IntConsumer task) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(taskCount)) {
            for (int i = 0; i < taskCount; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    await(startGate); // 모든 작업이 같은 출발선에서 시작하도록 대기
                    task.accept(index);
                }));
            }
            startGate.countDown();
        }

        Results results = new Results();
        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).get();
                results.successIndexes.add(i);
            } catch (ExecutionException e) {
                results.failures.add(e.getCause());
            }
        }
        return results;
    }

    private static class Results {
        private final List<Integer> successIndexes = new ArrayList<>();
        private final List<Throwable> failures = new ArrayList<>();

        List<Integer> successIndexes() {
            return successIndexes;
        }

        int successCount() {
            return successIndexes.size();
        }

        List<Throwable> failures() {
            return failures;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private long stockOf(Long id) {
        return jdbcTemplate.queryForObject("SELECT quantity FROM product WHERE id = ?", Long.class, id);
    }

    private long productCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM product", Long.class);
    }
}
