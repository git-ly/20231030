package com.example.microservices.core.recommendation;

import com.example.microservices.core.recommendation.persistence.RecommendationEntity;
import com.example.microservices.core.recommendation.persistence.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest(properties = {})
public class PersistenceTests extends MongoDbTestBase{
    @Autowired
    private RecommendationRepository repository;
    private RecommendationEntity savedEntity;
    @BeforeEach
    void setupDb(){
        repository.deleteAll().block();
        RecommendationEntity entity = new RecommendationEntity(1,2,"a",3,"c");
        savedEntity = repository.save(entity).block();
        assertEqualsRecommendation(entity,savedEntity);
    }
    @Test
    void create(){
        RecommendationEntity newEntity = new RecommendationEntity(1,3,"a",3,"c");
        repository.save(newEntity).block();
        RecommendationEntity foundEntity = repository.findById(newEntity.getId()).block();
        assertEqualsRecommendation(newEntity,foundEntity);
        assertEquals(2,(long)repository.count().block());
    }
    @Test
    void update(){
        savedEntity.setAuthor("a2");
        repository.save(savedEntity).block();
        RecommendationEntity foundEntity = repository.findById(savedEntity.getId()).block();
        assertEquals(1,(long)foundEntity.getVersion());
        assertEquals("a2",foundEntity.getAuthor());
    }
    @Test
    void delete(){
        repository.delete(savedEntity).block();
        assertFalse(repository.existsById(savedEntity.getId()).block());
    }
    @Test
    void getByProductId(){
        List<RecommendationEntity> entityList = repository.findByProductId(savedEntity.getProductId())
                .collectList().block();
        assertThat(entityList,hasSize(1));
        assertEqualsRecommendation(savedEntity,entityList.get(0));
    }
    @Test
    void duplicateError(){
        assertThrows(DuplicateKeyException.class,() -> {
            RecommendationEntity entity = new RecommendationEntity(1,2,"a",3,"c");
            repository.save(entity).block();
        });
    }
    @Test
    void optimisticLockError(){
        RecommendationEntity entity1 = repository.findById(savedEntity.getId()).block();
        RecommendationEntity entity2 = repository.findById(savedEntity.getId()).block();
        entity1.setAuthor("a1");
        repository.save(entity1).block();

        assertThrows(OptimisticLockingFailureException.class,() -> {
            entity2.setAuthor("a2");
            repository.save(entity2).block();
        });
        RecommendationEntity updatedEntity = repository.findById(savedEntity.getId()).block();
        assertEquals(1,(int)updatedEntity.getVersion());
        assertEquals("a1",updatedEntity.getAuthor());
    }
    private void assertEqualsRecommendation(RecommendationEntity expectedEntity,
                                            RecommendationEntity actualEntity){
        assertEquals(expectedEntity.getId(),actualEntity.getId());
        assertEquals(expectedEntity.getVersion(),actualEntity.getVersion());
        assertEquals(expectedEntity.getProductId(),actualEntity.getProductId());
        assertEquals(expectedEntity.getRecommendationId(),actualEntity.getRecommendationId());
        assertEquals(expectedEntity.getAuthor(),actualEntity.getAuthor());
        assertEquals(expectedEntity.getRating(),actualEntity.getRating());
        assertEquals(expectedEntity.getContent(),actualEntity.getContent());
    }

}
