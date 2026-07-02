package com.silvio.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.silvio.analytics.client.LendingClient;

/**
 * Test de contexto — verifica que el contexto Spring arranca correctamente.
 *
 * LendingClient es un @FeignClient que intenta conectarse a elending-service.
 * En tests no existe ese servicio, así que lo mockeamos para que Spring
 * pueda inyectarlo sin errores de conexión.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceApplicationTests {

    @MockBean
    private LendingClient lendingClient;

    @Test
    void contextLoads() {
        // Solo verifica que el contexto de Spring arranca sin errores
    }
}
