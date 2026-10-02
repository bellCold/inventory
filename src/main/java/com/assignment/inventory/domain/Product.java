package com.assignment.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private long quantity;

    public static Product register(String name) {
        Product product = new Product();
        product.name = name;
        return product;
    }

    public void increase(long amount) {
        this.quantity += amount;
    }

    public void decrease(long amount) {
        if (this.quantity < amount) {
            throw new InsufficientStockException(this.quantity, amount);
        }
        this.quantity -= amount;
    }
}
