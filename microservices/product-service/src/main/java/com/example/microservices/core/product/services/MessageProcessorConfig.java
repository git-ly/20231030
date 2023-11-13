package com.example.microservices.core.product.services;

import org.example.api.core.product.Product;
import org.example.api.core.product.ProductService;
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
    private final ProductService productService;
    @Autowired
    public MessageProcessorConfig(ProductService productService){
        this.productService = productService;
    }
    @Bean
    public Consumer<MicroEvent<Integer, Product>> messageProcessor(){
        return event -> {
            LOG.info("Process message created at {}...",event.getEventCreatedAt());
            switch (event.getEventType()){
                case CREATE -> {
                    Product product = event.getData();
                    LOG.info("Create product with ID: {}",product.getProductId());
                    productService.createProduct(product).block();
                }
                case DELETE -> {
                    int productId = event.getKey();
                    LOG.info("Delete product with ProductID: {}",productId);
                    productService.deleteProduct(productId).block();
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
