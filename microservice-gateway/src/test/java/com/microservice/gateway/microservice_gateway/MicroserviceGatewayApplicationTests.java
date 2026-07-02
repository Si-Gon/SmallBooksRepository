package com.microservice.gateway.microservice_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de contexto del gateway.
 *
 * @SpringBootTest con WebEnvironment.MOCK para no intentar levantar
 * un servidor real ni conectarse a Eureka/Config Server.
 * El perfil "test" desactiva esas dependencias externas.
 */
@SpringBootTest(webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MicroserviceGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
