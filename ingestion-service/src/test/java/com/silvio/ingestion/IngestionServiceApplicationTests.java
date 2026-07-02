package com.silvio.ingestion;

import com.silvio.ingestion.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de contexto básico.
 * StorageService tiene implementaciones concretas (Local/Database) que pueden
 * intentar acceder al sistema de archivos o a la BD al arrancar.
 * Lo mockeamos para un contexto limpio en tests.
 */
@SpringBootTest
@ActiveProfiles("test")
class IngestionServiceApplicationTests {

    @MockBean
    private StorageService storageService;

    @Test
    void contextLoads() {
    }
}
