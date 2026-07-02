package com.silvio.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de arranque del contexto de Spring.
 *
 * @ActiveProfiles("test") hace que Spring cargue application-test.properties
 * en lugar de application.properties, evitando que intente conectarse a MySQL,
 * Eureka o el Config Server durante las pruebas unitarias/integración local.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifica que el contexto de Spring arranca sin errores.
        // Si falta algún bean o hay un problema de configuración, este test falla.
    }
}
