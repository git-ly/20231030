package com.example.springcloud.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
properties = {
		"spring.cloud.config.enabled=false"
})
class GatewayApplicationTests {
	@Value("${app.eureka-username")
	private String username;
	@Value("${app.eureka-password")
	private String password;
	private TestRestTemplate testRestTemplate;
	@Autowired
	public void setTestRestTemplate(TestRestTemplate testRestTemplate){
		this.testRestTemplate = testRestTemplate.withBasicAuth(username,password);
	}

	@Test
	void contextLoads() {
	}

}
