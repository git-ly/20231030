package com.example.microservices.composite.product.services;

import com.example.microservices.composite.product.services.tracing.ObservationUtil;
import org.apache.http.client.methods.HttpHead;
import org.example.api.composite.product.*;
import org.example.api.core.product.Product;
import org.example.api.core.recommendation.Recommendation;
import org.example.api.core.review.Review;
import org.example.util.http.ServiceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

@RestController
public class ProductCompositeServiceImpl implements ProductCompositeService {
    private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeServiceImpl.class);
    private final SecurityContext nullSecCtx = new SecurityContextImpl();
    private final ServiceUtil serviceUtil;
    private final ObservationUtil observationUtil;
    private ProductCompositeIntegration integration;
    @Autowired
    public ProductCompositeServiceImpl(ServiceUtil serviceUtil,
                                       ObservationUtil observationUtil,
                                       ProductCompositeIntegration integration){
        this.serviceUtil = serviceUtil;
        this.observationUtil = observationUtil;
        this.integration = integration;
    }

    @Override
    public Mono<Void> createProduct(ProductAggregate body) {
        return observationWithProductInfo(body.getProductId(),() -> createProductInterval(body));
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        return observationWithProductInfo(productId,() -> deleteProductInternal(productId));
    }

    @Override
    public Mono<ProductAggregate> getProduct(HttpHeaders requestHeaders,int productId,int delay,int faultPercent) {
        return observationWithProductInfo(productId,() -> getProductInternal(requestHeaders,productId,delay,faultPercent));
    }
    private Mono<Void> deleteProductInternal(int productId){
        try{
            LOG.info("Will delete a product aggregate for product.id: {}",productId);
            return Mono.zip(r -> "",
                    getLogAuthorizationInfoMono(),
                    integration.deleteProduct(productId),
                    integration.deleteRecommendations(productId),
                    integration.deleteReviews(productId))
                    .doOnError(ex -> LOG.warn("delete failed: {}",ex.toString()))
                    .log(LOG.getName(),Level.FINE).then();
        }catch (RuntimeException re){
            LOG.warn("deleteCompositeProduct failed: {}",re.toString());
            throw re;
        }
    }
    private Mono<ProductAggregate> getProductInternal(HttpHeaders requestHeaders,int productId,int delay,int faultPercent){
        LOG.info("Will get composite product info for product.id={}",productId);
        HttpHeaders headers = getHeaders(requestHeaders,"X-group");
        return Mono.zip(values -> createProductAggregate(
                (SecurityContext) values[0],(Product) values[1],(List<Recommendation>) values[2],(List<Review>) values[3],serviceUtil.getServiceAddress()
        ),
                getSecurityContextMono(),
                integration.getProduct(headers,productId,delay,faultPercent),
                integration.getRecommendations(headers,productId).collectList(),
                integration.getReviews(headers,productId).collectList())
                .doOnError(ex -> LOG.warn("getCompositeProduct failed: {}",ex.toString()))
                .log(LOG.getName(),Level.FINE);
    }

    private HttpHeaders getHeaders(HttpHeaders requestHeaders,String... headers){
        LOG.trace("Will look for {} headers: {}",headers.length,headers);
        HttpHeaders h = new HttpHeaders();
        for (String header : headers){
            List<String> value = requestHeaders.get(header);
            if (value != null){
                h.addAll(header,value);
            }
        }
        LOG.trace("Will transfer {}, headers: {}",h.size(),h);
        return h;
    }
    private Mono<Void> createProductInterval(ProductAggregate body){
        try{
            List<Mono> monoList = new ArrayList<>();
            monoList.add(getLogAuthorizationInfoMono());
            LOG.info("Will create a new composite entity for product.id: {}",body.getProductId());
            Product product = new Product(body.getProductId(),body.getName(),body.getWeight(),null);
            monoList.add(integration.createProduct(product));
            if (body.getRecommendations() != null){
                body.getRecommendations().forEach(r -> {
                    Recommendation recommendation = new Recommendation(body.getProductId(),r.getRecommendationId(),r.getAuthor(),r.getRate(),r.getContent(),null);
                    monoList.add(integration.createRecommendation(recommendation));
                });
            }
            if (body.getReviews() != null){
                body.getReviews().forEach(r -> {
                    Review review = new Review(body.getProductId(),r.getReviewId(),r.getAuthor(),r.getSubject(),r.getContent(),null);
                    monoList.add(integration.createReview(review));
                });
            }
            LOG.debug("createCompositeProduct: composite entities created for productId: {}",body.getProductId());
            return Mono.zip(r -> "",monoList.toArray(new Mono[0]))
                    .doOnError(ex -> LOG.warn("createCompositeProduct failed: {}",ex.toString()))
                    .then();
        }catch (RuntimeException re){
            LOG.warn("createCompositeProduct failed: {}",re.toString());
            throw re;
        }
    }
    private <T> T observationWithProductInfo(int productInfo, Supplier<T> supplier){
        return observationUtil.observe(
                "composite observation",
                "product info",
                "productId",
                String.valueOf(productInfo),
                supplier
        );
    }
    private ProductAggregate createProductAggregate(
            SecurityContext sc,
            Product product,
            List<Recommendation> recommendations,
            List<Review> reviews,
            String serviceAddress
    ){
        logAuthorizationInfo(sc);
        int productId = product.getProductId();
        String name = product.getName();
        int weight = product.getWeight();
        List<RecommendationSummary> recommendationSummaries =
                (recommendations == null) ? null : recommendations.stream()
                        .map(r -> new RecommendationSummary(r.getRecommendationId(),
                                r.getAuthor(),r.getRate(),r.getContent()))
                        .collect(Collectors.toList());
        List<ReviewSummary> reviewSummaries = (reviews == null) ? null : reviews.stream()
                .map(r -> new ReviewSummary(r.getReviewId(),r.getAuthor(),
                        r.getSubject(),r.getContent()))
                .collect(Collectors.toList());
        String productAddress = product.getServiceAddress();
        String reviewAddress = (reviews != null && reviews.size() > 0) ?
                reviews.get(0).getServiceAddress() : "";
        String recommendationAddress = (recommendations != null &&
                recommendations.size() > 0) ? recommendations.get(0).getServiceAddress() : "";
        ServiceAddress serviceAddresses = new ServiceAddress(serviceAddress,productAddress,reviewAddress,recommendationAddress);
        return new ProductAggregate(productId,name,weight,recommendationSummaries,reviewSummaries,serviceAddresses);
    }
    private Mono<SecurityContext> getLogAuthorizationInfoMono(){
        return getSecurityContextMono().doOnNext(sc -> logAuthorizationInfo(sc));
    }
    private Mono<SecurityContext> getSecurityContextMono(){
        return ReactiveSecurityContextHolder.getContext().defaultIfEmpty(nullSecCtx);
    }
    private void logAuthorizationInfo(SecurityContext sc){
        if (sc != null && sc.getAuthentication() != null && sc.getAuthentication() instanceof JwtAuthenticationToken){
            Jwt jwtToken = ((JwtAuthenticationToken)sc.getAuthentication()).getToken();
            logAuthorizationInfo(jwtToken);
        }else{
            LOG.warn("No JWT based Authentication supplied, running tests are we?");
        }
    }
    private void logAuthorizationInfo(Jwt jwt){
        if (jwt == null){
            LOG.warn("No JWT supplied, running tests are we?");
        }else{
            if (LOG.isDebugEnabled()){
                URL issuer = jwt.getIssuer();
                List<String> audience = jwt.getAudience();
                Object subject = jwt.getClaims().get("sub");
                Object scopes = jwt.getClaims().get("scope");
                Object expires = jwt.getClaims().get("exp");

                LOG.debug("Authorization info: Subject: {}, scopes: {}, expires {}: issuer: {}, audience: {}",subject,scopes,expires,issuer,audience);
            }
        }
    }
}
