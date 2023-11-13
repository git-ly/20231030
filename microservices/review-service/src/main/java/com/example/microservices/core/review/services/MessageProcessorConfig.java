package com.example.microservices.core.review.services;

import org.example.api.core.review.Review;
import org.example.api.core.review.ReviewService;
import org.example.api.event.MicroEvent;
import org.example.api.exceptions.EventProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class MessageProcessorConfig {
    private static final Logger LOG = LoggerFactory.getLogger(MessageProcessorConfig.class);
    private final ReviewService reviewService;
    @Autowired
    public MessageProcessorConfig(ReviewService reviewService){
        this.reviewService = reviewService;
    }
    @Bean
    public Consumer<MicroEvent<Integer, Review>> messageProcessor(){
        return event -> {
            LOG.info("Process message created at {}...",event.getEventCreatedAt());
            switch (event.getEventType()){
                case CREATE -> {
                    Review review = event.getData();
                    LOG.info("Create review with ID: {}/{}",review.getProductId(),review.getReviewId());
                    reviewService.createReview(review).block();
                }
                case DELETE -> {
                    int productId = event.getKey();
                    LOG.info("Delete reviews with ProductID: {}",productId);
                    reviewService.deleteReviews(productId).block();
                }
                default -> {
                    String errorMessage = "Incorrect event type: " + event.getEventType() + ", expected a CREATE or DELETE event";
                    LOG.warn(errorMessage);
                    throw new EventProcessingException(errorMessage);
                }
            }
            LOG.info("Message processing done!");
        };
    }
}
