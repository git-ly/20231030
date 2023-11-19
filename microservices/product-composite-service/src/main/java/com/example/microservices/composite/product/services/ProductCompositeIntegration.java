package com.example.microservices.composite.product.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.example.api.core.product.Product;
import org.example.api.core.product.ProductService;
import org.example.api.core.recommendation.Recommendation;
import org.example.api.core.recommendation.RecommendationService;
import org.example.api.core.review.Review;
import org.example.api.core.review.ReviewService;
import org.example.api.event.MicroEvent;
import org.example.api.exceptions.InvalidInputException;
import org.example.api.exceptions.NotFoundException;
import org.example.util.http.HttpErrorInfo;
import org.example.util.http.ServiceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;

import static reactor.core.publisher.Flux.empty;

@Component
public class ProductCompositeIntegration implements ProductService,
        RecommendationService, ReviewService {
    private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);
    private static final String PRODUCT_SERVICE_URL = "http://product";
    private static final String RECOMMENDATION_SERVICE_URL = "http://recommendation";
    private static final String REVIEW_SERVICE_URL = "http://review";
    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final StreamBridge streamBridge;
    private final Scheduler publishEventScheduler;
    private final ServiceUtil serviceUtil;
    @Autowired
    public ProductCompositeIntegration(
            @Qualifier("publishEventScheduler")Scheduler publishEventScheduler,
            WebClient webClient,
            ObjectMapper mapper,
            StreamBridge streamBridge,
            ServiceUtil serviceUtil
    ){
        this.publishEventScheduler = publishEventScheduler;
        this.webClient = webClient;
        this.mapper =mapper;
        this.streamBridge = streamBridge;
        this.serviceUtil = serviceUtil;
    }

    @Override
    public Mono<Product> createProduct(Product body) {
        return Mono.fromCallable(() -> {
            sendMessage("products-out-0",
                    new MicroEvent<Integer,Product>(MicroEvent.Type.CREATE,
                            body.getProductId(),body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        return Mono.fromRunnable(() -> sendMessage("products-out-0",
                new MicroEvent(MicroEvent.Type.DELETE,productId,null)))
                .subscribeOn(publishEventScheduler).then();
    }

    @Retry(name = "product")
    @TimeLimiter(name = "product")
    @CircuitBreaker(name = "product",fallbackMethod = "getProductFallbackValue")
    @Override
    public Mono<Product> getProduct(int productId,int delay,int faultPercent) {
        URI url = UriComponentsBuilder.fromUriString(PRODUCT_SERVICE_URL + "/product/{product}?delay={delay}" +
                "&faultPercent={faultPercent}").build(productId,delay,faultPercent);
        LOG.debug("Will call the getProduct API on URL: {}",url);
        return webClient.get().uri(url).retrieve()
                .bodyToMono(Product.class)
                .log(LOG.getName(), Level.FINE)
                .onErrorMap(WebClientResponseException.class,
                        ex -> handleException(ex));
    }
    private Mono<Product> getProductFallbackValue(int productId, int delay, int faultPercent, CallNotPermittedException ex){
        if (productId == 13){
            String errMsg = "Product Id: " + productId
                    + " not found in fallback cache!";
            LOG.warn(errMsg);
            throw new NotFoundException(errMsg);
        }
        return Mono.just(new Product(productId,"Fallback product" +
                productId,productId,serviceUtil.getServiceAddress()));
    }
    public Mono<Health> getProductHealth(){
        return getHealth(PRODUCT_SERVICE_URL);
    }
    public Mono<Health> getRecommendationHealth(){
        return getHealth(RECOMMENDATION_SERVICE_URL);
    }
    public Mono<Health> getReviewHealth(){
        return getHealth(REVIEW_SERVICE_URL);
    }
    private Mono<Health> getHealth(String url){
        url += "/actuator/health";
        LOG.debug("Will call the Health API on URL: {}",url);
        return webClient.get().uri(url).retrieve().bodyToMono(String.class)
                .map(s -> new Health.Builder().up().build())
                .onErrorResume(ex -> Mono.just(new Health.Builder()
                        .down(ex).build()))
                .log(LOG.getName(),Level.FINE);
    }
    private String getErrorMessage(WebClientResponseException ex){
        try{
            return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class)
                    .getMessage();
        }catch (IOException ioex){
            return ex.getMessage();
        }
    }

    @Override
    public Mono<Recommendation> createRecommendation(Recommendation body) {
        return Mono.fromCallable(() -> {
            sendMessage("recommendations-out-0",
                    new MicroEvent(MicroEvent.Type.CREATE,body.getProductId(),body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Void> deleteRecommendations(int productId) {
        return Mono.fromRunnable(() -> sendMessage("recommendations-out-0",
                new MicroEvent(MicroEvent.Type.DELETE,productId,null)))
                .subscribeOn(publishEventScheduler).then();
    }

    @Override
    public Flux<Recommendation> getRecommendations(int productId) {
        String url = RECOMMENDATION_SERVICE_URL + "/recommendation?productId=" + productId;
        LOG.debug("Will call the getRecommendations API on URL: {}",url);
        return webClient.get().uri(url).retrieve()
                .bodyToFlux(Recommendation.class)
                .log(LOG.getName(),Level.FINE)
                .onErrorResume(error -> empty());
    }

    @Override
    public Mono<Review> createReview(Review body) {
        return Mono.fromCallable(() -> {
            sendMessage("reviews-out-0",new MicroEvent(MicroEvent.Type.CREATE,
                    body.getProductId(),body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Void> deleteReviews(int productId) {
        return Mono.fromRunnable(() -> sendMessage("reviews-out-0",
                new MicroEvent(MicroEvent.Type.DELETE,productId,null)))
                .subscribeOn(publishEventScheduler).then();
    }

    @Override
    public Flux<Review> getReviews(int productId) {
        String url = REVIEW_SERVICE_URL + "/review?productId=" + productId;
        LOG.debug("Will call the getReviews API on URL: {}",url);
        return webClient.get().uri(url).retrieve().bodyToFlux(Review.class)
                .log(LOG.getName(),Level.FINE)
                .onErrorResume(error -> empty());
    }
    private void sendMessage(String bindingName,MicroEvent event){
        LOG.debug("Sending a {} message to {}",event.getEventType(),bindingName);
        Message message = MessageBuilder.withPayload(event)
                .setHeader("partitionKey",event.getKey())
                .build();
        streamBridge.send(bindingName,message);
    }
    private Throwable handleException(Throwable ex){
        if (!(ex instanceof WebClientResponseException)){
            LOG.warn("Got a unexpected error: {}, will rethrow it",ex.toString());
            return ex;
        }
        WebClientResponseException wcre = (WebClientResponseException) ex;
        return switch (HttpStatus.resolve(wcre.getStatusCode().value())){
            case NOT_FOUND -> new NotFoundException(getErrorMessage(wcre));
            case UNPROCESSABLE_ENTITY -> new InvalidInputException(getErrorMessage(wcre));
            default -> {
                LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", wcre
                        .getMessage());
                LOG.warn("Error body: {}",wcre.getResponseBodyAsString());
                yield ex;
            }
        };
    }

}
