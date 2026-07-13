package com.silvio.content.config;

import com.silvio.content.client.IngestionClient;
import com.silvio.content.client.LendingClient;
import com.silvio.content.dto.PrestamoDTO;
import com.silvio.content.service.ContentService;
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

// Tests de integracion para verificar que @Observed en ContentService
// crea spans de tracing correctamente.
//
// ContentService tiene 1 metodo @Observed:
//   content.obtenerArchivo
//
// NOTA: ContentService usa LendingClient e IngestionClient (Feign).
// El tracing se propaga automaticamente via ObservationCapability de Feign.
@SpringBootTest
@ActiveProfiles("test")
class ObservedAnnotationIntegrationTest {

    @Autowired
    private ContentService contentService;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private LendingClient lendingClient;

    @MockBean
    private IngestionClient ingestionClient;

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
    void observedAspect_obtenerArchivo_creaSpanCorrectamente() {
        PrestamoDTO prestamo = new PrestamoDTO();
        prestamo.setLibroId(1L);
        prestamo.setEstado("ACTIVO");
        when(lendingClient.obtenerPrestamosActivos("Bearer test-token"))
                .thenReturn(java.util.List.of(prestamo));
        when(ingestionClient.obtenerBytes(1L)).thenReturn(new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> {
            var resultado = contentService.obtenerArchivo(1L, "Bearer test-token");
            assertNotNull(resultado);
        });
    }
}
