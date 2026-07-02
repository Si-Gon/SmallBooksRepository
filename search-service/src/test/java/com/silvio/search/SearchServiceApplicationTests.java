package com.silvio.search;

import com.silvio.search.client.CatalogClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de contexto básico.
 * CatalogClient es un @FeignClient — sin Eureka no se puede resolver,
 * así que lo mockeamos para que Spring pueda inyectarlo.
 */
@SpringBootTest
@ActiveProfiles("test")
class SearchServiceApplicationTests {

    @MockBean
    private CatalogClient catalogClient;

    @Test
    void contextLoads() {
    }
}
