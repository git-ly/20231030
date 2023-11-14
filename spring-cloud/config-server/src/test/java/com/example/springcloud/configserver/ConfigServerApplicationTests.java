package com.example.springcloud.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
properties = {"spring.profiles.active=native"})
class ConfigServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
