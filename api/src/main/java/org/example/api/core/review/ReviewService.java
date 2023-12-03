package org.example.api.core.review;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ReviewService {
    Mono<Review> createReview(Review body);
    Mono<Void> deleteReviews(int productId);
    @GetMapping(
            value = "/review",
            produces = "application/json"
    )
    Flux<Review> getReviews(
            @RequestHeader HttpHeaders headers,
            @RequestParam(value = "productId",required = true) int productId);
}
