package com.assignment.inventory.domain;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(long currentQuantity, long requestedQuantity) {
        super("재고가 부족합니다. (현재 재고: " + currentQuantity + ", 요청 수량: " + requestedQuantity + ")");
    }
}
