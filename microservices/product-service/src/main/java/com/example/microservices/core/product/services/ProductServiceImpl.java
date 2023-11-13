package com.example.microservices.core.product.services;

import com.example.microservices.core.product.persistence.ProductEntity;
import com.example.microservices.core.product.persistence.ProductRepository;
import org.example.api.core.product.Product;
import org.example.api.core.product.ProductService;
import org.example.api.exceptions.InvalidInputException;
import org.example.api.exceptions.NotFoundException;
import org.example.util.http.ServiceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.logging.Level;

@RestController
public class ProductServiceImpl implements ProductService {
    private static final Logger LOG = LoggerFactory.getLogger(ProductServiceImpl.class);
    private final ServiceUtil serviceUtil;
    private final ProductRepository repository;
    private final ProductMapper mapper;
    @Autowired
    public ProductServiceImpl(ServiceUtil serviceUtil,
                              ProductMapper mapper,
                              ProductRepository repository){
        this.serviceUtil = serviceUtil;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<Product> createProduct(Product product) {
        if (product.getProductId() < 1){
            throw new InvalidInputException("Invalid productId: " + product.getProductId());
        }
        ProductEntity entity = mapper.apiToEntity(product);
        Mono<Product> newEntity = repository.save(entity)
                .log(LOG.getName(),Level.FINE)
                .onErrorMap(DuplicateKeyException.class,
                        ex -> new InvalidInputException("Duplicate key, Product Id: " + product.getProductId()))
                .map(e -> mapper.entityToApi(e));
        return newEntity;
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        if (productId < 1){
            throw new InvalidInputException("Invalid productId: " + productId);
        }
        LOG.debug("deleteProduct: tries to delete an entity with productId: {}",
                productId);
        return repository.findByProductId(productId)
                .log(LOG.getName(),Level.FINE)
                .map(e -> repository.delete(e))
                .flatMap(e -> e);
    }

    @Override
    public Mono<Product> getProduct(int productId) {
        if (productId < 1) {
            throw new InvalidInputException("Invalid productId: " + productId);
        }
        LOG.info("Will get product info for id={}", productId);
        return repository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new NotFoundException("No product found for productId: " + productId)))
                .log(LOG.getName(), Level.FINE)
                .map(e -> mapper.entityToApi(e))
                .map(e -> setServiceAddress(e));
    }
    private Product setServiceAddress(Product e){
        e.setServiceAddress(serviceUtil.getServiceAddress());
        return e;
    }
}
