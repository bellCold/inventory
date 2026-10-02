package com.assignment.inventory.application;

import com.assignment.inventory.domain.Product;
import com.assignment.inventory.domain.ProductNotFoundException;
import com.assignment.inventory.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    @Transactional
    public Product inbound(String name, long quantity) {
        Product product = productRepository.findForUpdateByName(name).orElseGet(() -> productRepository.save(Product.register(name)));
        product.increase(quantity);
        return product;
    }

    @Transactional
    public Product outbound(Long id, long quantity) {
        Product product = productRepository.findForUpdateById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.decrease(quantity);
        return product;
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
