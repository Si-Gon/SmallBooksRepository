package com.silvio.elending.repository;

import com.silvio.elending.model.Prestamo;
import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración del PrestamoRepository usando @DataJpaTest con H2.
 *
 * Verifica que los derived queries y el mapeo JPA funcionan correctamente.
 */
@DataJpaTest
@ActiveProfiles("test")
class PrestamoRepositoryTest {

    @Autowired
    private PrestamoRepository prestamoRepository;

    private Prestamo prestamoActivo;
    private Prestamo prestamoVencido;
    private Prestamo prestamoActivo2;

    @BeforeEach
    void setUp() {
        prestamoRepository.deleteAll();

        prestamoActivo = new Prestamo();
        prestamoActivo.setUsuarioId("usuario1");
        prestamoActivo.setLibroId(1L);
        prestamoActivo.setFechaInicio(LocalDateTime.now().minusDays(5));
        prestamoActivo.setFechaVencimiento(LocalDateTime.now().plusDays(9));
        prestamoActivo.setEstado(EstadoPrestamo.ACTIVO);

        prestamoVencido = new Prestamo();
        prestamoVencido.setUsuarioId("usuario1");
        prestamoVencido.setLibroId(2L);
        prestamoVencido.setFechaInicio(LocalDateTime.now().minusDays(20));
        prestamoVencido.setFechaVencimiento(LocalDateTime.now().minusDays(6));
        prestamoVencido.setEstado(EstadoPrestamo.VENCIDO);

        prestamoActivo2 = new Prestamo();
        prestamoActivo2.setUsuarioId("usuario2");
        prestamoActivo2.setLibroId(3L);
        prestamoActivo2.setFechaInicio(LocalDateTime.now().minusDays(1));
        prestamoActivo2.setFechaVencimiento(LocalDateTime.now().plusDays(13));
        prestamoActivo2.setEstado(EstadoPrestamo.ACTIVO);

        prestamoRepository.save(prestamoActivo);
        prestamoRepository.save(prestamoVencido);
        prestamoRepository.save(prestamoActivo2);
    }

    // =========================================================
    // findByUsuarioIdAndEstado
    // =========================================================

    @Test
    void findByUsuarioIdAndEstado_debeRetornarActivosDelUsuario() {
        List<Prestamo> resultado = prestamoRepository
                .findByUsuarioIdAndEstado("usuario1", EstadoPrestamo.ACTIVO);

        assertEquals(1, resultado.size());
        assertEquals(1L, resultado.get(0).getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.get(0).getEstado());
    }

    @Test
    void findByUsuarioIdAndEstado_sinResultados_debeRetornarListaVacia() {
        List<Prestamo> resultado = prestamoRepository
                .findByUsuarioIdAndEstado("usuario_inexistente", EstadoPrestamo.ACTIVO);

        assertTrue(resultado.isEmpty());
    }

    // =========================================================
    // findByLibroIdAndEstado
    // =========================================================

    @Test
    void findByLibroIdAndEstado_debeRetornarActivosDelLibro() {
        List<Prestamo> resultado = prestamoRepository
                .findByLibroIdAndEstado(1L, EstadoPrestamo.ACTIVO);

        assertEquals(1, resultado.size());
        assertEquals(1L, resultado.get(0).getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.get(0).getEstado());
    }

    @Test
    void findByLibroIdAndEstado_sinResultados_debeRetornarListaVacia() {
        List<Prestamo> resultado = prestamoRepository
                .findByLibroIdAndEstado(999L, EstadoPrestamo.ACTIVO);

        assertTrue(resultado.isEmpty());
    }

    // =========================================================
    // findByEstadoAndFechaVencimientoBefore
    // =========================================================

    @Test
    void findByEstadoAndFechaVencimientoBefore_debeRetornarVencidos() {
        List<Prestamo> resultado = prestamoRepository
                .findByEstadoAndFechaVencimientoBefore(
                        EstadoPrestamo.ACTIVO, LocalDateTime.now().minusDays(1));

        // Solo prestamoActivo2 tiene fecha de vencimiento futura
        // prestamoActivo tiene fecha de vencimiento futura (now + 9)
        // prestamoVencido ya está VENCIDO
        // Ninguno debería estar ACTIVO con vencimiento antes de ayer
        assertTrue(resultado.isEmpty());
    }

    @Test
    void findByEstadoAndFechaVencimientoBefore_conFechaFutura_debeRetornarActivos() {
        // Buscar ACTIVOS con vencimiento antes de una fecha futura
        List<Prestamo> resultado = prestamoRepository
                .findByEstadoAndFechaVencimientoBefore(
                        EstadoPrestamo.ACTIVO, LocalDateTime.now().plusDays(20));

        // Ambos préstamos activos vencen antes de 20 días desde ahora
        assertEquals(2, resultado.size());
    }

    // =========================================================
    // findByUsuarioId
    // =========================================================

    @Test
    void findByUsuarioId_debeRetornarHistorialCompleto() {
        List<Prestamo> resultado = prestamoRepository.findByUsuarioId("usuario1");

        assertEquals(2, resultado.size()); // activo + vencido
    }

    @Test
    void findByUsuarioId_sinResultados_debeRetornarListaVacia() {
        List<Prestamo> resultado = prestamoRepository.findByUsuarioId("usuario_inexistente");

        assertTrue(resultado.isEmpty());
    }

    // =========================================================
    // findAll
    // =========================================================

    @Test
    void findAll_debeRetornarTodosLosPrestamos() {
        List<Prestamo> resultado = prestamoRepository.findAll();

        assertEquals(3, resultado.size());
    }

    // =========================================================
    // Integridad de datos
    // =========================================================

    @Test
    void save_debePersistirPrestamoConTodosLosCampos() {
        // Given
        Prestamo nuevo = new Prestamo();
        nuevo.setUsuarioId("usuario_nuevo");
        nuevo.setLibroId(100L);
        nuevo.setFechaInicio(LocalDateTime.now());
        nuevo.setFechaVencimiento(LocalDateTime.now().plusDays(7));
        nuevo.setEstado(EstadoPrestamo.ACTIVO);

        // When
        Prestamo guardado = prestamoRepository.save(nuevo);

        // Then
        assertNotNull(guardado.getId());
        assertEquals("usuario_nuevo", guardado.getUsuarioId());
        assertEquals(100L, guardado.getLibroId());
        assertNotNull(guardado.getFechaInicio());
        assertNotNull(guardado.getFechaVencimiento());
        assertEquals(EstadoPrestamo.ACTIVO, guardado.getEstado());
    }

    // =========================================================
    // delete
    // =========================================================

    @Test
    void eliminar_Prestamo_debeReflejarseEnConsulta() {
        // Given
        long totalAntes = prestamoRepository.count();

        // When
        prestamoRepository.delete(prestamoActivo);

        // Then
        assertEquals(totalAntes - 1, prestamoRepository.count());
        assertTrue(prestamoRepository.findById(prestamoActivo.getId()).isEmpty());
    }
}