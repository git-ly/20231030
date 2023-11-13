package com.example.springcloud.authorizationserver;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.Base64;

@SpringBootTest(properties = {"eureka.client.enabled=false","spring.cloud.config.enabled=false"})
@AutoConfigureMockMvc
class AuthorizationServerApplicationTests {
	@Autowired
	MockMvc mvc;
	@Test
	void requestTokenUsingClientCredentialsGrantType() throws Exception {
		String base64Credentials = Base64.getEncoder().encodeToString("writer:secret-writer".getBytes());
		this.mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
				.param("grant_type","client_credentials")
				.header("Authorization","Basic " + base64Credentials))
				.andExpect(MockMvcResultMatchers.status().isOk());
	}

	@Test
	void requestOpenidConfiguration() throws Exception {
		this.mvc.perform(MockMvcRequestBuilders.get("/.well-known/openid-configuration"))
				.andExpect(MockMvcResultMatchers.status().isOk());
	}

	@Test
	void requestJwkSet() throws Exception {
		this.mvc.perform(MockMvcRequestBuilders.get("/oauth2/jwks"))
				.andExpect(MockMvcResultMatchers.status().isOk());
	}

	@Test
	void healthy() throws Exception {
		this.mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.status",
						Matchers.is("UP")));
	}

	@Test
	void contextLoads() {
	}

}
