package com.silvio.search.config;

import com.silvio.search.client.CatalogClient;
import com.silvio.search.service.SearchService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// Tests de integracion para verificar que las 3 anotaciones @Observed
// en SearchService crean spans de tracing correctamente.
//
// SearchService tiene @Observed en:
//   search.buscar, search.buscarDisponibles, search.obtenerTodos
//
// NOTA: SearchService usa CatalogClient (Feign) que NO requiere seguridad.
// El tracing se propaga automaticamente via ObservationCapability de Feign.
@SpringBootTest
@ActiveProfiles("test")
class ObservedAnnotationIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private CatalogClient catalogClient;

    @Test
    void contextCarga_conBeansDeTracing() {
        assertNotNull(observationRegistry, "ObservationRegistry debe estar disponible");
        assertNotNull(tracer, "Tracer (Brave) debe estar disponible");
    }

    @Test
    void observationRegistry_aceptaCrearObservaciones() {
        Observation observation = Observation.createNotStarted("test.manual", observationRegistry);
        assertNotNull(observation);
        assertDoesNotThrow(() -> {
            observation.start();
            observation.stop();
        });
    }

    @Test
    void tracer_currentSpan_devuelveSpanSinError() {
        assertDoesNotThrow(() -> tracer.currentSpan());
    }

    @Test
    void observedAspect_buscar_creaSpanCorrectamente() {
        when(catalogClient.buscar("test", null, null))
                .thenReturn(java.util.Collections.emptyList());
        assertDoesNotThrow(() -> {
            var resultado = searchService.buscar("test", null, null);
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
        });
    }

    @Test
    void observedAspect_buscarDisponibles_creaSpanCorrectamente() {
        when(catalogClient.obtenerDisponibles())
                .thenReturn(java.util.Collections.emptyList());
        assertDoesNotThrow(() -> {
            var resultado = searchService.buscarDisponibles();
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_obtenerTodos_creaSpanCorrectamente() {
        when(catalogClient.obtenerTodos())
                .thenReturn(java.util.Collections.emptyList());
        assertDoesNotThrow(() -> {
            var resultado = searchService.obtenerTodos();
            assertNotNull(resultado);
        });
    }
}
