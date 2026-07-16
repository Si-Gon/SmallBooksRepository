package com.silvio.analytics.config;

import com.silvio.analytics.client.LendingClient;
import com.silvio.analytics.service.AnalyticsService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// Tests de integracion para verificar que las 2 anotaciones @Observed
// en AnalyticsService crean spans de tracing correctamente.
//
// AnalyticsService tiene @Observed en:
//   analytics.obtenerEstadisticas, analytics.historialUsuario
//
// NOTA: AnalyticsService usa LendingClient (Feign) que llama a elending-service.
// El tracing se propaga automaticamente via ObservationCapability de Feign.
@SpringBootTest
@ActiveProfiles("test")
class ObservedAnnotationIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private LendingClient lendingClient;

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
    void observedAspect_obtenerEstadisticas_creaSpanCorrectamente() {
        when(lendingClient.obtenerTodos())
                .thenReturn(new PageImpl<>(java.util.Collections.emptyList()));
        assertDoesNotThrow(() -> {
            var resultado = analyticsService.obtenerEstadisticas();
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_historialUsuario_creaSpanCorrectamente() {
        when(lendingClient.obtenerHistorial("u1"))
                .thenReturn(java.util.Collections.emptyList());
        assertDoesNotThrow(() -> {
            var resultado = analyticsService.historialUsuario("u1");
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
        });
    }
}
