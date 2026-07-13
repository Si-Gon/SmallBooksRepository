package com.microservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "native"})
class MicroserviceConfigApplicationTests {

	@Test
	void contextLoads() {
	}

}
