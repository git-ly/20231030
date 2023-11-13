package com.example.microservices.composite.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.core.product.Product;
import org.example.api.event.MicroEvent;
import org.junit.jupiter.api.Test;

import static com.example.microservices.composite.product.IsSameEvent.sameEventExceptCreatedAt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class IsSameEventTests {
    ObjectMapper mapper = new ObjectMapper();
    @Test
    void testEventObjectCompare() throws JsonProcessingException {
        MicroEvent<Integer, Product> event1 = new MicroEvent<>(MicroEvent.Type.CREATE,1,new Product(1,"name",1,null));
        MicroEvent<Integer,Product> event2 = new MicroEvent<>(MicroEvent.Type.CREATE,1,new Product(1,"name",1,null));
        MicroEvent<Integer,Product> event3 = new MicroEvent<>(MicroEvent.Type.DELETE,1,null);
        MicroEvent<Integer,Product> event4 = new MicroEvent<>(MicroEvent.Type.CREATE,1,new Product(2,"name",1,null));
        String event1Json = mapper.writeValueAsString(event1);
        assertThat(event1Json,is(sameEventExceptCreatedAt(event2)));
        assertThat(event1Json,not(sameEventExceptCreatedAt(event3)));
        assertThat(event1Json,not(sameEventExceptCreatedAt(event4)));
    }
}
