package com.example.microservices.core.recommendation;

import com.example.microservices.core.recommendation.persistence.RecommendationRepository;
import org.example.api.core.recommendation.Recommendation;
import org.example.api.event.MicroEvent;
import org.example.api.exceptions.InvalidInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static reactor.core.publisher.Mono.just;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
properties = {})
class RecommendationServiceApplicationTests extends MongoDbTestBase{
	@Autowired
	private WebTestClient client;
	@Autowired
	private RecommendationRepository repository;
	@Autowired
	@Qualifier("messageProcessor")
	private Consumer<MicroEvent<Integer,Recommendation>> messageProcessor;

	@BeforeEach
	void setupDb(){
		repository.deleteAll().block();
	}
	@Test
	void getRecommendationsByProductId(){
		int productId = 1;
		sendCreateRecommendationEvent(productId,1);
		sendCreateRecommendationEvent(productId,2);
		sendCreateRecommendationEvent(productId,3);
		assertEquals(3,repository.findByProductId(productId).count().block());
		getAndVerifyRecommendationsByProductId(productId,HttpStatus.OK)
				.jsonPath("$.length()").isEqualTo(3)
				.jsonPath("$[2].productId").isEqualTo(productId)
				.jsonPath("$[2].recommendationId").isEqualTo(3);
	}
	@Test
	void duplicateError(){
		int productId = 1;
		int recommendationId = 1;
		sendCreateRecommendationEvent(productId,recommendationId);
		assertEquals(1,(long)repository.count().block());
		InvalidInputException thrown = assertThrows(
				InvalidInputException.class,
				() -> sendCreateRecommendationEvent(productId,recommendationId),
				"Expected a InvalidInputException here!"
		);
		assertEquals("Duplicate key, Product Id: 1, Recommendation Id: 1",thrown.getMessage());
		assertEquals(1,(long)repository.count().block());
	}
	@Test
	void deleteRecommendations(){
		int productId = 1;
		int recommendationId = 1;
		sendCreateRecommendationEvent(productId,recommendationId);
		assertEquals(1,(long)repository.findByProductId(productId).count().block());

		sendDeleteRecommendationEvent(productId);
		assertEquals(0,(long)repository.findByProductId(productId).count().block());
		sendDeleteRecommendationEvent(productId);
	}
	@Test
	void getRecommendationsMissingParameter(){
		getAndVerifyRecommendationsByProductId("",HttpStatus.BAD_REQUEST)
				.jsonPath("$.path").isEqualTo("/recommendation")
				.jsonPath("$.message")
				.isEqualTo("Required query parameter 'productId' is not present.");
	}
	@Test
	void getRecommendationsInvalidParameter(){
		getAndVerifyRecommendationsByProductId("?productId=no-integer",
				HttpStatus.BAD_REQUEST)
				.jsonPath("$.path").isEqualTo("/recommendation")
				.jsonPath("$.message").isEqualTo("Type mismatch.");
	}
	@Test
	void getRecommendationsNotFound(){
		getAndVerifyRecommendationsByProductId("?productId=113",HttpStatus.OK)
				.jsonPath("$.length()").isEqualTo(0);
	}
	@Test
	void getRecommendationsInvalidParameterNegativeValue(){
		int productIdInvalid = -1;
		getAndVerifyRecommendationsByProductId("?productId=" + productIdInvalid,
				HttpStatus.UNPROCESSABLE_ENTITY)
				.jsonPath("$.path").isEqualTo("/recommendation")
				.jsonPath("$.message").isEqualTo("Invalid productId: " +
						productIdInvalid);
	}
	private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(int productId,
																				 HttpStatus expectedStatus){
		return getAndVerifyRecommendationsByProductId("?productId=" + productId,
				expectedStatus);
	}
	private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(String productIdQuery,
																				 HttpStatus expectedStatus){
		return client.get()
				.uri("/recommendation" + productIdQuery)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isEqualTo(expectedStatus)
				.expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectBody();
	}
	private WebTestClient.BodyContentSpec postAndVerifyRecommendation(int productId,int recommendationId,
																	  HttpStatus expectedStatus){
		Recommendation recommendation = new Recommendation(productId,recommendationId,
				"Author " + recommendationId,recommendationId,"Content " + recommendationId,
				"SA");
		return client.post()
				.uri("/recommendation")
				.body(just(recommendation),Recommendation.class)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isEqualTo(expectedStatus)
				.expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectBody();
	}
	private WebTestClient.BodyContentSpec deleteAndVerifyRecommendationsByProductId(int productId,
																					HttpStatus expectedStatus){
		return client.delete()
				.uri("/recommendation?productId=" + productId)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isEqualTo(expectedStatus)
				.expectBody();
	}
	private void sendCreateRecommendationEvent(int productId,int recommendationId){
		Recommendation recommendation = new Recommendation(productId,recommendationId,"Author " + recommendationId,
				recommendationId,"Content " + recommendationId,"SA");
		MicroEvent<Integer,Recommendation> event = new MicroEvent<>(MicroEvent.Type.CREATE,productId,recommendation);
		messageProcessor.accept(event);
	}
	private void sendDeleteRecommendationEvent(int productId){
		MicroEvent<Integer,Recommendation> event = new MicroEvent<>(MicroEvent.Type.DELETE,productId,null);
		messageProcessor.accept(event);
	}
}
