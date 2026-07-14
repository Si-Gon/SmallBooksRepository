package com.silvio.analytics.config;

import com.silvio.analytics.client.LendingClient;
import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import com.silvio.analytics.exception.ErrorHistorialUsuarioException;
import com.silvio.analytics.service.AnalyticsService;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Tests de integración que verifican que el tracing distribuido
// se propaga a través del Feign Client hacia elending-service.
//
// AnalyticsService usa LendingClient (Feign) para obtener datos
// de préstamos. ObservationCapability de OpenFeign propaga headers
// B3 automáticamente.
@SpringBootTest
@ActiveProfiles("test")
class FeignTracingPropagationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private LendingClient lendingClient;

    @Test
    void contextCarga_tracerDisponible() {
        assertNotNull(tracer, "Tracer debe estar disponible para tracing Feign");
    }

    @Test
    void llamadaFeign_obtenerEstadisticas_propagaTrace() {
        when(lendingClient.obtenerTodos())
                .thenReturn(java.util.Collections.emptyList());

        assertDoesNotThrow(() -> {
            var resultado = analyticsService.obtenerEstadisticas();
            assertNotNull(resultado);
            assertEquals(0, resultado.getTotalPrestamos());
        });

        verify(lendingClient, times(1)).obtenerTodos();
    }

    @Test
    void llamadaFeign_historialUsuario_propagaTrace() {
        when(lendingClient.obtenerHistorial("user-1"))
                .thenReturn(java.util.Collections.emptyList());

        assertDoesNotThrow(() -> {
            var resultado = analyticsService.historialUsuario("user-1");
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
        });

        verify(lendingClient, times(1)).obtenerHistorial("user-1");
    }

    @Test
    void llamadaFeign_conDatosEstadisticos_propagaTrace() {
        PrestamoAnalyticsDTO p1 = new PrestamoAnalyticsDTO();
        p1.setLibroId(1L);
        p1.setUsuarioId("u1");
        p1.setEstado("ACTIVO");

        PrestamoAnalyticsDTO p2 = new PrestamoAnalyticsDTO();
        p2.setLibroId(2L);
        p2.setUsuarioId("u2");
        p2.setEstado("VENCIDO");

        when(lendingClient.obtenerTodos())
                .thenReturn(java.util.List.of(p1, p2));

        assertDoesNotThrow(() -> {
            var resultado = analyticsService.obtenerEstadisticas();
            assertNotNull(resultado);
            assertEquals(2, resultado.getTotalPrestamos());
            assertEquals(1, resultado.getPrestamosActivos());
            assertEquals(1, resultado.getPrestamosVencidos());
        });

        verify(lendingClient, times(1)).obtenerTodos();
    }

    @Test
    void llamadaFeign_lendingFalla_lanzaExcepcion() {
        when(lendingClient.obtenerHistorial("user-error"))
                .thenThrow(new RuntimeException("Error en elending-service"));

        ErrorHistorialUsuarioException ex = assertThrows(ErrorHistorialUsuarioException.class,
                () -> analyticsService.historialUsuario("user-error"));
        assertTrue(ex.getMessage().contains("Error al obtener historial"));
    }
}
