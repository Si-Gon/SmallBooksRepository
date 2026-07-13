package com.silvio.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

	@MockBean
	private ConnectionFactory connectionFactory;
	
	@Test
	void contextLoads() {
	}

}
