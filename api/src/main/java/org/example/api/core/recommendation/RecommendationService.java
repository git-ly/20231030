package org.example.api.core.recommendation;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RecommendationService {
    Mono<Recommendation> createRecommendation(Recommendation body);
    Mono<Void> deleteRecommendations(int productId);
    @GetMapping(
            value = "/recommendation",
            produces = "application/json"
    )
    Flux<Recommendation> getRecommendations(
            @RequestHeader HttpHeaders headers,
            @RequestParam(value = "productId",required = true)int productId
    );
}
