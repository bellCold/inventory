package com.assignment.inventory.api;

import com.assignment.inventory.application.ProductService;
import com.assignment.inventory.domain.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping("/inbound")
    public ProductResponse inbound(@Valid @RequestBody InboundRequest request) {
        return ProductResponse.from(productService.inbound(request.name(), request.quantity()));
    }

    @PostMapping("/{id}/outbound")
    public ProductResponse outbound(@PathVariable Long id, @Valid @RequestBody OutboundRequest request) {
        return ProductResponse.from(productService.outbound(id, request.quantity()));
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return ProductResponse.from(productService.getProduct(id));
    }

    public record InboundRequest(@NotBlank String name, @NotNull @Positive Long quantity) {
    }

    public record OutboundRequest(@NotNull @Positive Long quantity) {
    }

    public record ProductResponse(Long id, String name, long quantity) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getName(), product.getQuantity());
        }
    }
}
