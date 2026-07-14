package com.silvio.analytics.controller;

import com.silvio.analytics.dto.EstadisticasDTO;
import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import com.silvio.analytics.exception.ErrorDatosPrestamosException;
import com.silvio.analytics.exception.ErrorHistorialUsuarioException;
import com.silvio.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del AnalyticsController.
 *
 * Este servicio NO tiene Spring Security, así que @WebMvcTest funciona
 * de forma directa sin necesidad de excluir filtros ni configurar TestSecurityConfig.
 *
 * Mapeamos AnalyticsService con @MockBean para controlar qué devuelve
 * cada endpoint sin tocar el FeignClient real.
 */
@WebMvcTest(AnalyticsController.class)
@ActiveProfiles("test")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    private PrestamoAnalyticsDTO prestamo(Long id, String usuarioId, Long libroId, String estado) {
        PrestamoAnalyticsDTO p = new PrestamoAnalyticsDTO();
        p.setId(id);
        p.setUsuarioId(usuarioId);
        p.setLibroId(libroId);
        p.setEstado(estado);
        p.setFechaInicio(LocalDateTime.now());
        p.setFechaVencimiento(LocalDateTime.now().plusDays(14));
        return p;
    }

    // =====================================================================
    // GET /api/analytics/estadisticas
    // =====================================================================

    @Test
    void obtenerEstadisticas_debeRetornar200ConDatos() throws Exception {
        EstadisticasDTO stats = new EstadisticasDTO();
        stats.setTotalPrestamos(10L);
        stats.setPrestamosActivos(7L);
        stats.setPrestamosVencidos(3L);
        Map<Long, Long> libros = new LinkedHashMap<>();
        libros.put(1L, 5L);
        libros.put(2L, 3L);
        stats.setLibrosMasPrestados(libros);
        Map<String, Long> usuarios = new LinkedHashMap<>();
        usuarios.put("silvio", 4L);
        stats.setUsuariosMasActivos(usuarios);

        when(analyticsService.obtenerEstadisticas()).thenReturn(stats);

        mockMvc.perform(get("/api/analytics/estadisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrestamos").value(10))
                .andExpect(jsonPath("$.prestamosActivos").value(7))
                .andExpect(jsonPath("$.prestamosVencidos").value(3))
                .andExpect(jsonPath("$.librosMasPrestados").exists())
                .andExpect(jsonPath("$.usuariosMasActivos").exists());

        verify(analyticsService).obtenerEstadisticas();
    }

    @Test
    void obtenerEstadisticas_errorInterno_debeRetornar503() throws Exception {
        // ErrorDatosPrestamosException → GlobalExceptionHandler devuelve 503
        when(analyticsService.obtenerEstadisticas())
            .thenThrow(new ErrorDatosPrestamosException("Connection refused"));

        mockMvc.perform(get("/api/analytics/estadisticas"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    // =====================================================================
    // GET /api/analytics/historial/{usuarioId}
    // =====================================================================

    @Test
    void historialUsuario_existente_debeRetornar200ConLista() throws Exception {
        List<PrestamoAnalyticsDTO> historial = List.of(
            prestamo(1L, "silvio", 1L, "ACTIVO"),
            prestamo(2L, "silvio", 2L, "VENCIDO")
        );
        when(analyticsService.historialUsuario("silvio")).thenReturn(historial);

        mockMvc.perform(get("/api/analytics/historial/silvio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].usuarioId").value("silvio"))
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[1].estado").value("VENCIDO"));

        verify(analyticsService).historialUsuario("silvio");
    }

    @Test
    void historialUsuario_sinPrestamos_debeRetornar200ConListaVacia() throws Exception {
        when(analyticsService.historialUsuario("nuevo")).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/historial/nuevo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void historialUsuario_errorEnServicio_debeRetornar503() throws Exception {
        when(analyticsService.historialUsuario("fallo"))
            .thenThrow(new ErrorHistorialUsuarioException("fallo"));

        mockMvc.perform(get("/api/analytics/historial/fallo"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    // =====================================================================
    // Error interno (RuntimeException) → 500 en todos los endpoints
    // =====================================================================

    @Test
    void obtenerEstadisticas_errorInterno_debeRetornar500() throws Exception {
        when(analyticsService.obtenerEstadisticas())
            .thenThrow(new RuntimeException("Error inesperado al calcular estadísticas"));

        mockMvc.perform(get("/api/analytics/estadisticas"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(analyticsService).obtenerEstadisticas();
    }

    @Test
    void historialUsuario_errorInterno_debeRetornar500() throws Exception {
        when(analyticsService.historialUsuario("error500"))
            .thenThrow(new RuntimeException("Error inesperado al consultar historial"));

        mockMvc.perform(get("/api/analytics/historial/error500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(analyticsService).historialUsuario("error500");
    }
}
