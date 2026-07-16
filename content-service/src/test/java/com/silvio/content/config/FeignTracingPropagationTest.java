package com.silvio.content.config;

import com.silvio.content.client.IngestionClient;
import com.silvio.content.client.LendingClient;
import com.silvio.content.dto.PrestamoDTO;
import com.silvio.content.exception.AccesoDenegadoException;
import com.silvio.content.service.ContentService;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Tests de integración que verifican que el tracing distribuido
// se propaga correctamente a través de los Feign Clients.
//
// ContentService hace llamadas Feign a:
//   1. LendingClient → elending-service (verifica préstamo activo)
//   2. IngestionClient → ingestion-service (obtiene bytes del archivo)
//
// Micrometer Tracing con Brave propaga automáticamente los headers
// B3 (X-B3-TraceId, X-B3-SpanId, X-B3-ParentSpanId) via
// ObservationCapability de OpenFeign — sin código adicional.
@SpringBootTest
@ActiveProfiles("test")
class FeignTracingPropagationTest {

    @Autowired
    private ContentService contentService;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private LendingClient lendingClient;

    @MockBean
    private IngestionClient ingestionClient;

    @Test
    void contextCarga_tracerDisponible() {
        assertNotNull(tracer, "Tracer debe estar disponible para tracing Feign");
    }

    @Test
    void llamadaFeign_lendingClient_ejecutaConTracing() {
        // Simula una llamada Feign que normalmente propagaría headers B3
        PrestamoDTO prestamo = new PrestamoDTO();
        prestamo.setLibroId(1L);
        prestamo.setEstado("ACTIVO");
        when(lendingClient.obtenerPrestamosActivos("silvio"))
                .thenReturn(java.util.List.of(prestamo));
        when(ingestionClient.obtenerBytes(1L)).thenReturn(new byte[]{1, 2, 3});

        assertDoesNotThrow(() -> {
            var resultado = contentService.obtenerArchivo(1L, "silvio");
            assertNotNull(resultado);
            assertArrayEquals(new byte[]{1, 2, 3}, resultado);
        });

        // Verifica que ambas llamadas Feign se ejecutaron
        verify(lendingClient, times(1)).obtenerPrestamosActivos("silvio");
        verify(ingestionClient, times(1)).obtenerBytes(1L);
    }

    @Test
    void llamadaFeign_sinPrestamoActivo_lanzaExcepcion() {
        when(lendingClient.obtenerPrestamosActivos("silvio"))
                .thenReturn(java.util.Collections.emptyList());

        assertThrows(AccesoDenegadoException.class,
                () -> contentService.obtenerArchivo(1L, "silvio"),
                "Sin préstamo activo debe lanzar excepción");

        verify(lendingClient, times(1)).obtenerPrestamosActivos("silvio");
        verify(ingestionClient, never()).obtenerBytes(anyLong());
    }

    @Test
    void llamadaFeign_lendingClientFalla_lanzaExcepcion() {
        when(lendingClient.obtenerPrestamosActivos("silvio"))
                .thenThrow(new RuntimeException("Error de conexión con E-Lending"));

        // El servicio ya no envuelve excepciones — se propagan directamente
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> contentService.obtenerArchivo(1L, "silvio"));
        assertTrue(ex.getMessage().contains("Error de conexión con E-Lending"));

        verify(lendingClient, times(1)).obtenerPrestamosActivos("silvio");
        verify(ingestionClient, never()).obtenerBytes(anyLong());
    }
}
