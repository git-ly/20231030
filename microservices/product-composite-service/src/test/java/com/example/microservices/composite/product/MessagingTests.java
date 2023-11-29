package com.example.microservices.composite.product;

import org.example.api.composite.product.ProductAggregate;
import org.example.api.composite.product.RecommendationSummary;
import org.example.api.composite.product.ReviewSummary;
import org.example.api.core.product.Product;
import org.example.api.core.recommendation.Recommendation;
import org.example.api.core.review.Review;
import org.example.api.event.MicroEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static com.example.microservices.composite.product.IsSameEvent.sameEventExceptCreatedAt;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.ACCEPTED;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {TestSecurityConfig.class},
        properties = {
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.cloud.stream.defaultBinder=rabbit"
        }
)
@Import({TestChannelBinderConfiguration.class})
class MessagingTests {
    private static final Logger LOG = LoggerFactory.getLogger(MessagingTests.class);
    @Autowired
    private WebTestClient client;
    @Autowired
    private OutputDestination target;
    @BeforeEach
    void setUp(){
        purgeMessages("products");
        purgeMessages("recommendations");
        purgeMessages("reviews");
    }
    @Test
    void createCompositeProduct1(){
        ProductAggregate composite = new ProductAggregate(1,"name",1,null,null,null);
        postAndVerifyProduct(composite,ACCEPTED);
        final List<String> productMessages = getMessages("products");
        final List<String> recommendationMessages = getMessages("recommendations");
        final List<String> reviewMessages = getMessages("reviews");
        assertEquals(1,productMessages.size());
        MicroEvent<Integer, Product> expectedEvent =
                new MicroEvent<>(MicroEvent.Type.CREATE,composite.getProductId(),
                        new Product(composite.getProductId(),composite.getName(),
                                composite.getWeight(),null));
        assertThat(productMessages.get(0),is(sameEventExceptCreatedAt(expectedEvent)));
        assertEquals(0,recommendationMessages.size());
        assertEquals(0,reviewMessages.size());
    }
    @Test
    void createCompositeProduct2(){
        ProductAggregate composite = new ProductAggregate(1,"name",1,
                singletonList(new RecommendationSummary(1,"a",1,"c")),
                singletonList(new ReviewSummary(1,"a","s","c")),null);
        postAndVerifyProduct(composite,ACCEPTED);
        final List<String> productMessages = getMessages("products");
        final List<String> recommendationMessages = getMessages("recommendations");
        final List<String> reviewMessages = getMessages("reviews");
        assertEquals(1,productMessages.size());
        MicroEvent<Integer,Product> expectedProductEvent =
                new MicroEvent<>(MicroEvent.Type.CREATE,composite.getProductId(),
                        new Product(composite.getProductId(),composite.getName(),composite.getWeight(),
                                null));
        assertThat(productMessages.get(0),is(sameEventExceptCreatedAt(expectedProductEvent)));
        assertEquals(1,recommendationMessages.size());
        RecommendationSummary rec = composite.getRecommendations().get(0);
        MicroEvent<Integer,Recommendation> expectedRecommendationEvent =
        new MicroEvent<>(MicroEvent.Type.CREATE,composite.getProductId(),
                new Recommendation(composite.getProductId(),rec.getRecommendationId(),rec.getAuthor(),
                        rec.getRate(),rec.getContent(),null));
        assertThat(recommendationMessages.get(0),is(sameEventExceptCreatedAt(expectedRecommendationEvent)));
        assertEquals(1,reviewMessages.size());
        ReviewSummary rev = composite.getReviews().get(0);
        MicroEvent<Integer, Review> expectedReviewEvent =
                new MicroEvent<>(MicroEvent.Type.CREATE,composite.getProductId(),new Review(composite.getProductId(),
                        rev.getReviewId(),rev.getAuthor(),rev.getSubject(),rev.getContent(),null));
        assertThat(reviewMessages.get(0),is(sameEventExceptCreatedAt(expectedReviewEvent)));
    }
    @Test
    void deleteCompositeProduct(){
        deleteAndVerifyProduct(1,ACCEPTED);
        final List<String> productMessages = getMessages("products");
        final List<String> recommendationMessages = getMessages("recommendations");
        final List<String> reviewMessages = getMessages("reviews");
        assertEquals(1,productMessages.size());
        MicroEvent<Integer,Product> expectedProductEvent = new MicroEvent<>(MicroEvent.Type.DELETE,
                1,null);
        assertThat(productMessages.get(0),is(sameEventExceptCreatedAt(expectedProductEvent)));
        assertEquals(1,recommendationMessages.size());
        MicroEvent<Integer,Product> expectedRecommendationEvent = new MicroEvent<>(MicroEvent.Type.DELETE,
                1,null);
        assertThat(recommendationMessages.get(0),is(sameEventExceptCreatedAt(expectedRecommendationEvent)));
        assertEquals(1,reviewMessages.size());
        MicroEvent<Integer,Product> expectedReviewEvent = new MicroEvent<>(MicroEvent.Type.DELETE,
                1,null);
        assertThat(reviewMessages.get(0),is(sameEventExceptCreatedAt(expectedReviewEvent)));
    }
    private void purgeMessages(String bindingName){
        getMessages(bindingName);
    }
    private List<String> getMessages(String bindingName){
        List<String> messages = new ArrayList<>();
        boolean anyMoreMessages = true;
        while (anyMoreMessages){
            Message<byte[]> message = getMessage(bindingName);
            if (message == null){
                anyMoreMessages = false;
            }else{
                messages.add(new String(message.getPayload()));
            }
        }
        return messages;
    }
    private Message<byte[]> getMessage(String bindingName){
        try{
            return target.receive(0,bindingName);
        }catch (NullPointerException npe){
            LOG.error("getMessage() received a NPE with binding = {}",bindingName);
            return null;
        }
    }
    private void postAndVerifyProduct(ProductAggregate compositeProduct,HttpStatus expectedStatus){
        client.post()
                .uri("/product-composite")
                .body(Mono.just(compositeProduct),ProductAggregate.class)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
    }
    private void deleteAndVerifyProduct(int productId, HttpStatus expectedStatus){
        client.delete()
                .uri("/product-composite/" + productId)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
    }
}
