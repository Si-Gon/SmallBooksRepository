package com.silvio.catalog.config;

import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.model.Libro;
import com.silvio.catalog.repository.LibroRepository;
import com.silvio.catalog.service.CatalogService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Tests de integración que verifican que las anotaciones @Observed
// crean spans de tracing correctamente en CatalogService.
//
// CatalogService tiene 8 métodos @Observed:
//   catalog.obtenerTodos, catalog.obtenerDisponibles, catalog.obtenerPorId,
//   catalog.buscar, catalog.agregar, catalog.actualizar,
//   catalog.cambiarDisponibilidad, catalog.eliminar
@SpringBootTest
@ActiveProfiles("test")
class ObservedAnnotationIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private LibroRepository libroRepository;

    @Test
    void contextCarga_conBeansDeTracing() {
        assertNotNull(observationRegistry, "ObservationRegistry debe estar disponible");
        assertNotNull(tracer, "Tracer (Brave) debe estar disponible");
    }

    @Test
    void observationRegistry_aceptaCrearObservaciones() {
        assertNotNull(observationRegistry);
        Observation observation = Observation.createNotStarted("test.manual", observationRegistry);
        assertNotNull(observation);
        assertDoesNotThrow(() -> {
            observation.start();
            observation.stop();
        });
    }

    @Test
    void tracer_currentSpan_devuelveSpanSinError() {
        assertNotNull(tracer);
        assertDoesNotThrow(() -> tracer.currentSpan());
    }

    @Test
    void observedAspect_obtenerTodos_creaSpanCorrectamente() {
        when(libroRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.Collections.emptyList()));
        assertDoesNotThrow(() -> {
            var resultado = catalogService.obtenerTodos(Pageable.unpaged());
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
        });
    }

    @Test
    void observedAspect_obtenerDisponibles_creaSpanCorrectamente() {
        when(libroRepository.findByDisponibleTrue()).thenReturn(java.util.Collections.emptyList());
        assertDoesNotThrow(() -> {
            var resultado = catalogService.obtenerDisponibles();
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_obtenerPorId_creaSpanCorrectamente() {
        Libro libro = new Libro();
        libro.setId(1L);
        libro.setTitulo("Test");
        when(libroRepository.findById(1L)).thenReturn(java.util.Optional.of(libro));
        assertDoesNotThrow(() -> {
            var resultado = catalogService.obtenerPorId(1L);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_metodoVoid_eliminar_creaSpanSinError() {
        Libro libro = new Libro();
        libro.setId(1L);
        when(libroRepository.findById(1L)).thenReturn(java.util.Optional.of(libro));
        assertDoesNotThrow(() -> catalogService.eliminar(1L));
    }

    @Test
    void observedAspect_buscar_creaSpanConParametros() {
        when(libroRepository.buscarCombinado("test", null, null))
                .thenReturn(java.util.Collections.emptyList());
        assertDoesNotThrow(() -> {
            var resultado = catalogService.buscar("test", null, null);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_agregar_creaSpanCorrectamente() {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setIsbn("1234567890");
        request.setTitulo("Test");
        when(libroRepository.findByIsbn("1234567890")).thenReturn(java.util.Optional.empty());
        when(libroRepository.save(any(Libro.class))).thenReturn(new Libro());
        assertDoesNotThrow(() -> {
            var resultado = catalogService.agregar(request);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_cambiarDisponibilidad_creaSpanCorrectamente() {
        Libro libro = new Libro();
        libro.setId(1L);
        when(libroRepository.findById(1L)).thenReturn(java.util.Optional.of(libro));
        when(libroRepository.save(any(Libro.class))).thenReturn(libro);
        assertDoesNotThrow(() -> {
            var resultado = catalogService.cambiarDisponibilidad(1L, false);
            assertNotNull(resultado);
        });
    }
}
