package com.assignment.inventory.infrastructure;

import com.assignment.inventory.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findForUpdateById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findForUpdateByName(String name);
}
