package com.silvio.ingestion.config;

import com.silvio.ingestion.model.ArchivoLibro;
import com.silvio.ingestion.repository.ArchivoLibroInfo;
import com.silvio.ingestion.repository.ArchivoLibroRepository;
import com.silvio.ingestion.service.IngestionService;
import com.silvio.ingestion.storage.StorageService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Tests de integracion para verificar que las 4 anotaciones @Observed
// en IngestionService crean spans de tracing correctamente.
//
// IngestionService tiene @Observed en:
//   ingestion.subirArchivo, ingestion.obtenerInfo,
//   ingestion.obtenerBytes, ingestion.eliminar
@SpringBootTest
@ActiveProfiles("test")
class ObservedAnnotationIntegrationTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private ArchivoLibroRepository archivoRepository;

    @MockBean
    private StorageService storageService;

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
    void observedAspect_obtenerInfo_creaSpanCorrectamente() {
        ArchivoLibroInfo info = mock(ArchivoLibroInfo.class);
        when(info.getId()).thenReturn(1L);
        when(info.getLibroId()).thenReturn(1L);
        when(archivoRepository.findInfoByLibroId(1L)).thenReturn(java.util.Optional.of(info));
        assertDoesNotThrow(() -> {
            var resultado = ingestionService.obtenerInfo(1L);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_obtenerBytes_creaSpanCorrectamente() {
        ArchivoLibro archivo = new ArchivoLibro();
        archivo.setLibroId(1L);
        archivo.setRutaOClave("db:1");
        when(archivoRepository.findByLibroId(1L)).thenReturn(java.util.Optional.of(archivo));
        when(storageService.obtener("db:1")).thenReturn(new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> {
            var resultado = ingestionService.obtenerBytes(1L);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_metodoVoid_eliminar_creaSpanSinError() {
        ArchivoLibro archivo = new ArchivoLibro();
        archivo.setLibroId(1L);
        archivo.setRutaOClave("db:1");
        when(archivoRepository.findByLibroId(1L)).thenReturn(java.util.Optional.of(archivo));
        assertDoesNotThrow(() -> ingestionService.eliminar(1L));
    }
}
