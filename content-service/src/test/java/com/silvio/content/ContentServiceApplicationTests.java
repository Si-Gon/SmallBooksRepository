package com.silvio.content;

import com.silvio.content.client.IngestionClient;
import com.silvio.content.client.LendingClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de contexto básico.
 * LendingClient e IngestionClient son @FeignClient — sin Eureka no pueden
 * resolverse, así que los mockeamos para que Spring pueda inyectarlos.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContentServiceApplicationTests {

    @MockBean
    private LendingClient lendingClient;

    @MockBean
    private IngestionClient ingestionClient;

    @Test
    void contextLoads() {
    }
}
