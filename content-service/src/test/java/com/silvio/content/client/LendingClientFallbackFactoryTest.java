package com.silvio.content.client;

import com.silvio.content.dto.PrestamoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests para LendingClientFallbackFactory
// Verifica que cuando el circuito esta abierto o elending-service no responde,
// se devuelva una lista vacia como respuesta degradada.
class LendingClientFallbackFactoryTest {

    private LendingClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new LendingClientFallbackFactory();
    }

    @Test
    void create_conExcepcion_obtenerPrestamosActivos_retornaListaVacia() {
        // Given — cualquier excepcion que active el CB
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoDTO> resultado = clienteFallback.obtenerPrestamosActivos("silvio");

        // Then — lista vacia, no null
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcionConNullMessage_obtenerPrestamosActivos_retornaListaVacia() {
        // Given — excepcion con mensaje null (caso borde)
        RuntimeException causa = new RuntimeException();

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoDTO> resultado = clienteFallback.obtenerPrestamosActivos("silvio");

        // Then — debe funcionar sin NPE
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_usuarioIdNull_retornaListaVacia() {
        // Given — usuarioId null (caso borde)
        RuntimeException causa = new RuntimeException("Timeout");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoDTO> resultado = clienteFallback.obtenerPrestamosActivos(null);

        // Then — debe retornar vacio sin lanzar excepcion
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_usuarioIdVacio_retornaListaVacia() {
        // Given
        RuntimeException causa = new RuntimeException("Service unavailable");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoDTO> resultado = clienteFallback.obtenerPrestamosActivos("");

        // Then
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void fallback_retornaListaInmutable() {
        // Given
        RuntimeException causa = new RuntimeException("Error generico");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoDTO> resultado = clienteFallback.obtenerPrestamosActivos("silvio");

        // Then — Collections.emptyList() retorna una lista inmutable
        assertNotNull(resultado);
        assertThrows(UnsupportedOperationException.class,
                () -> resultado.add(new PrestamoDTO()));
    }

    @Test
    void create_conExcepcionDeRed_retornaListaVaciaConsistente() {
        // Given — simula una excepcion tipica de red
        RuntimeException causa = new RuntimeException(
                "connect: Address is invalid, or socket could not be bound");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoDTO> resultado = clienteFallback.obtenerPrestamosActivos("silvio");

        // Then — respuesta consistente independientemente del tipo de excepcion
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_llamadasMultiples_retornaListaVaciaSiempre() {
        // Given
        RuntimeException causa = new RuntimeException("Read timed out");
        LendingClient clienteFallback = fallbackFactory.create(causa);

        // When & Then — multiples llamadas siempre retornan lista vacia
        assertTrue(clienteFallback.obtenerPrestamosActivos("silvio1").isEmpty());
        assertTrue(clienteFallback.obtenerPrestamosActivos("silvio2").isEmpty());
        assertTrue(clienteFallback.obtenerPrestamosActivos("silvio3").isEmpty());
    }
}
