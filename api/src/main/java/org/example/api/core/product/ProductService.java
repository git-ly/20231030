package org.example.api.core.product;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

public interface ProductService {
    Mono<Product> createProduct(Product product);
    Mono<Void> deleteProduct(int productId);
    @GetMapping(
            value = "/product/{productId}",
            produces = "application/json"
    )
    Mono<Product> getProduct(
            @RequestHeader HttpHeaders headers,
            @PathVariable int productId,
            @RequestParam(value = "delay",required = false,defaultValue = "0") int delay,
            @RequestParam(value = "faultPercent",required = false,defaultValue = "0")
                             int faultPercent);
}
