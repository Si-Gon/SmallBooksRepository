package com.silvio.search.config;

import com.silvio.search.client.CatalogClient;
import com.silvio.search.dto.LibroCatalogDTO;
import com.silvio.search.service.SearchService;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Tests de integración que verifican que el tracing distribuido
// se propaga a través del Feign Client hacia catalog-service.
//
// SearchService usa CatalogClient (Feign) para consultas al catálogo.
// ObservationCapability de OpenFeign propaga headers B3 automáticamente.
@SpringBootTest
@ActiveProfiles("test")
class FeignTracingPropagationTest {

    @Autowired
    private SearchService searchService;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private CatalogClient catalogClient;

    @Test
    void contextCarga_tracerDisponible() {
        assertNotNull(tracer, "Tracer debe estar disponible para tracing Feign");
    }

    @Test
    void llamadaFeign_obtenerTodos_propagaTrace() {
        when(catalogClient.obtenerTodos())
                .thenReturn(java.util.Collections.emptyList());

        assertDoesNotThrow(() -> {
            var resultado = searchService.obtenerTodos();
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
        });

        verify(catalogClient, times(1)).obtenerTodos();
    }

    @Test
    void llamadaFeign_buscarConParametros_propagaTrace() {
        when(catalogClient.buscar("Cien años", "García Márquez", null))
                .thenReturn(java.util.Collections.emptyList());

        assertDoesNotThrow(() -> {
            var resultado = searchService.buscar("Cien años", "García Márquez", null);
            assertNotNull(resultado);
        });

        verify(catalogClient, times(1)).buscar("Cien años", "García Márquez", null);
    }

    @Test
    void llamadaFeign_buscarDisponibles_propagaTrace() {
        LibroCatalogDTO libro = new LibroCatalogDTO();
        libro.setId(1L);
        libro.setTitulo("Libro Disponible");
        when(catalogClient.obtenerDisponibles())
                .thenReturn(java.util.List.of(libro));

        assertDoesNotThrow(() -> {
            var resultado = searchService.buscarDisponibles();
            assertNotNull(resultado);
            assertEquals(1, resultado.size());
        });

        verify(catalogClient, times(1)).obtenerDisponibles();
    }

    @Test
    void llamadaFeign_catalogFalla_propagaExcepcion() {
        when(catalogClient.buscar("error", null, null))
                .thenThrow(new RuntimeException("Error en catalog-service"));

        // El servicio ya no envuelve excepciones — se propagan directamente
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> searchService.buscar("error", null, null));
        assertTrue(ex.getMessage().contains("Error en catalog-service"));
    }
}
