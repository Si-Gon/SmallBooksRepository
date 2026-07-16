package com.silvio.analytics.service;

import com.silvio.analytics.client.LendingClient;
import com.silvio.analytics.dto.EstadisticasDTO;
import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de AnalyticsService.
 *
 * Estrategia: @ExtendWith(MockitoExtension) en lugar de @SpringBootTest
 * → Solo crea los objetos necesarios (sin contexto Spring completo)
 * → LendingClient se mockea con @Mock, se inyecta con @InjectMocks
 * → Mucho más rápido y aislado: probamos la lógica de cálculo de estadísticas
 *
 * ¿Por qué es importante testear la lógica aquí?
 * La mayor parte del valor de AnalyticsService está en el procesamiento
 * de la lista de préstamos (groupingBy, sorting, top 5). Eso se puede
 * testear perfectamente sin Spring, solo con datos en memoria.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private LendingClient lendingClient;

    @InjectMocks
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
    // obtenerEstadisticas()
    // =====================================================================

    @Test
    void obtenerEstadisticas_conPrestamos_calculaCorrectamente() {
        // Simulamos 5 préstamos: 3 activos, 2 vencidos
        // El libro 1 se presta 3 veces → debe aparecer primero en librosMasPrestados
        List<PrestamoAnalyticsDTO> content = List.of(
            prestamo(1L, "silvio", 1L, "ACTIVO"),
            prestamo(2L, "silvio", 1L, "ACTIVO"),
            prestamo(3L, "ana",    1L, "VENCIDO"),
            prestamo(4L, "ana",    2L, "ACTIVO"),
            prestamo(5L, "pedro",  2L, "VENCIDO")
        );
        Page<PrestamoAnalyticsDTO> mock = new PageImpl<>(content);
        when(lendingClient.obtenerTodos()).thenReturn(mock);

        EstadisticasDTO resultado = analyticsService.obtenerEstadisticas();

        assertThat(resultado.getTotalPrestamos()).isEqualTo(5L);
        assertThat(resultado.getPrestamosActivos()).isEqualTo(3L);
        assertThat(resultado.getPrestamosVencidos()).isEqualTo(2L);

        // Libro 1 aparece 3 veces, libro 2 aparece 2 veces
        assertThat(resultado.getLibrosMasPrestados()).containsKey(1L);
        assertThat(resultado.getLibrosMasPrestados().get(1L)).isEqualTo(3L); // silvio tiene 2 del libro 1, pero el total es 3
        // Verificamos que el libro 1 es el primero (más prestado)
        Long primerLibro = resultado.getLibrosMasPrestados().keySet().iterator().next();
        assertThat(primerLibro).isEqualTo(1L);

        // silvio tiene 2 préstamos, ana tiene 2, pedro tiene 1
        assertThat(resultado.getUsuariosMasActivos()).containsKey("silvio");
        assertThat(resultado.getUsuariosMasActivos()).containsKey("ana");
        assertThat(resultado.getUsuariosMasActivos()).containsKey("pedro");

        verify(lendingClient).obtenerTodos();
    }

    @Test
    void obtenerEstadisticas_listaVacia_retornaEstadisticasEnCero() {
        when(lendingClient.obtenerTodos()).thenReturn(new PageImpl<>(List.of()));

        EstadisticasDTO resultado = analyticsService.obtenerEstadisticas();

        assertThat(resultado.getTotalPrestamos()).isEqualTo(0L);
        assertThat(resultado.getPrestamosActivos()).isEqualTo(0L);
        assertThat(resultado.getPrestamosVencidos()).isEqualTo(0L);
        assertThat(resultado.getLibrosMasPrestados()).isEmpty();
        assertThat(resultado.getUsuariosMasActivos()).isEmpty();
    }

    @Test
    void obtenerEstadisticas_errorEnFeign_lanzaRuntimeException() {
        // Si el FeignClient falla (elending-service caído), la excepción se propaga directamente
        when(lendingClient.obtenerTodos()).thenThrow(new RuntimeException("Connection refused"));

        // El servicio ya no envuelve excepciones — se propagan directamente
        assertThatThrownBy(() -> analyticsService.obtenerEstadisticas())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Connection refused");
    }

    @Test
    void obtenerEstadisticas_soloMaximoCincoLibros_enTopLibros() {
        // Si hay más de 5 libros distintos, el top debe limitarse a 5
        List<PrestamoAnalyticsDTO> content = List.of(
            prestamo(1L,  "u1", 10L, "ACTIVO"),
            prestamo(2L,  "u2", 20L, "ACTIVO"),
            prestamo(3L,  "u3", 30L, "ACTIVO"),
            prestamo(4L,  "u4", 40L, "ACTIVO"),
            prestamo(5L,  "u5", 50L, "ACTIVO"),
            prestamo(6L,  "u6", 60L, "ACTIVO"),  // este 6to libro no debe aparecer en el top 5
            prestamo(7L,  "u7", 70L, "ACTIVO")   // tampoco este
        );
        Page<PrestamoAnalyticsDTO> mock = new PageImpl<>(content);
        when(lendingClient.obtenerTodos()).thenReturn(mock);

        EstadisticasDTO resultado = analyticsService.obtenerEstadisticas();

        // El top 5 no debe tener más de 5 entradas
        assertThat(resultado.getLibrosMasPrestados()).hasSizeLessThanOrEqualTo(5);
        assertThat(resultado.getUsuariosMasActivos()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void obtenerEstadisticas_conPageConMetadatos_usaGetContentCorrectamente() {
        // Simula una respuesta paginada con multiples paginas
        // El servicio solo debe usar page.getContent() para los calculos
        List<PrestamoAnalyticsDTO> content = List.of(
            prestamo(1L, "silvio", 1L, "ACTIVO"),
            prestamo(2L, "silvio", 1L, "ACTIVO"),
            prestamo(3L, "ana",    2L, "VENCIDO")
        );
        // Page con metadata: pagina 1 de 3, total 13 elementos
        Page<PrestamoAnalyticsDTO> mock = new PageImpl<>(content,
                org.springframework.data.domain.PageRequest.of(1, 5), 13);
        when(lendingClient.obtenerTodos()).thenReturn(mock);

        EstadisticasDTO resultado = analyticsService.obtenerEstadisticas();

        // Los calculos solo usan el contenido de la pagina actual
        assertThat(resultado.getTotalPrestamos()).isEqualTo(3L); // content.size(), no totalElements
        assertThat(resultado.getPrestamosActivos()).isEqualTo(2L);
        assertThat(resultado.getPrestamosVencidos()).isEqualTo(1L);
        assertThat(resultado.getLibrosMasPrestados()).hasSize(2);
        assertThat(resultado.getUsuariosMasActivos()).hasSize(2);

        verify(lendingClient).obtenerTodos();
    }

    // =====================================================================
    // historialUsuario()
    // =====================================================================

    @Test
    void historialUsuario_existente_retornaLista() {
        List<PrestamoAnalyticsDTO> historial = List.of(
            prestamo(1L, "silvio", 1L, "ACTIVO"),
            prestamo(2L, "silvio", 2L, "VENCIDO")
        );
        when(lendingClient.obtenerHistorial("silvio")).thenReturn(historial);

        List<PrestamoAnalyticsDTO> resultado = analyticsService.historialUsuario("silvio");

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allMatch(p -> "silvio".equals(p.getUsuarioId()));
        verify(lendingClient).obtenerHistorial("silvio");
    }

    @Test
    void historialUsuario_listaVacia_retornaListaVacia() {
        when(lendingClient.obtenerHistorial("usuario_sin_prestamos")).thenReturn(List.of());

        List<PrestamoAnalyticsDTO> resultado = analyticsService.historialUsuario("usuario_sin_prestamos");

        assertThat(resultado).isEmpty();
    }

    @Test
    void historialUsuario_errorEnFeign_lanzaRuntimeException() {
        when(lendingClient.obtenerHistorial("silvio"))
            .thenThrow(new RuntimeException("Feign error"));

        // El servicio ya no envuelve excepciones — se propagan directamente
        assertThatThrownBy(() -> analyticsService.historialUsuario("silvio"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Feign error");
    }
}
