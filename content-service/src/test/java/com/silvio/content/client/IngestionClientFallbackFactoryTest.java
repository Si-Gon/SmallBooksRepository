package com.silvio.content.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests para IngestionClientFallbackFactory
// Verifica que cuando el circuito esta abierto o ingestion-service no responde,
// se devuelva un arreglo de bytes vacio como respuesta degradada.
class IngestionClientFallbackFactoryTest {

    private IngestionClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new IngestionClientFallbackFactory();
    }

    @Test
    void create_conExcepcion_obtenerBytes_retornaArregloVacio() {
        // Given — cualquier excepcion que active el CB
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        IngestionClient clienteFallback = fallbackFactory.create(causa);
        byte[] resultado = clienteFallback.obtenerBytes(42L);

        // Then — arreglo vacio, no null
        assertNotNull(resultado);
        assertEquals(0, resultado.length);
    }

    @Test
    void create_conExcepcionConNullMessage_obtenerBytes_retornaArregloVacio() {
        // Given — excepcion con mensaje null (caso borde)
        RuntimeException causa = new RuntimeException();

        // When
        IngestionClient clienteFallback = fallbackFactory.create(causa);
        byte[] resultado = clienteFallback.obtenerBytes(1L);

        // Then — debe funcionar sin NPE
        assertNotNull(resultado);
        assertEquals(0, resultado.length);
    }

    @Test
    void create_conExcepcion_libroIdNull_retornaArregloVacio() {
        // Given — libroId null (caso borde)
        RuntimeException causa = new RuntimeException("Timeout");

        // When
        IngestionClient clienteFallback = fallbackFactory.create(causa);
        byte[] resultado = clienteFallback.obtenerBytes(null);

        // Then — no debe lanzar NPE
        assertNotNull(resultado);
        assertEquals(0, resultado.length);
    }

    @Test
    void create_conExcepcion_libroIdNegativo_retornaArregloVacio() {
        // Given — libroId con valor invalido
        RuntimeException causa = new RuntimeException("Service unavailable");

        // When
        IngestionClient clienteFallback = fallbackFactory.create(causa);
        byte[] resultado = clienteFallback.obtenerBytes(-1L);

        // Then — debe retornar vacio sin lanzar excepcion
        assertNotNull(resultado);
        assertEquals(0, resultado.length);
    }

    @Test
    void create_conExcepcionDeRed_retornaArregloVacioConsistente() {
        // Given — simula una excepcion tipica de red
        RuntimeException causa = new RuntimeException(
                "connect: Address is invalid, or socket could not be bound");

        // When
        IngestionClient clienteFallback = fallbackFactory.create(causa);
        byte[] resultado = clienteFallback.obtenerBytes(100L);

        // Then — respuesta consistente independientemente del tipo de excepcion
        assertNotNull(resultado);
        assertEquals(0, resultado.length);
    }

    @Test
    void create_conExcepcion_llamadasMultiples_retornaArregloVacioSiempre() {
        // Given
        RuntimeException causa = new RuntimeException("Read timed out");
        IngestionClient clienteFallback = fallbackFactory.create(causa);

        // When & Then — multiples llamadas con distintos ids siempre retornan vacio
        assertEquals(0, clienteFallback.obtenerBytes(1L).length);
        assertEquals(0, clienteFallback.obtenerBytes(999L).length);
        assertEquals(0, clienteFallback.obtenerBytes(Long.MAX_VALUE).length);
    }

    @Test
    void fallback_retornaArregloVacio_noEsNull() {
        // Given
        RuntimeException causa = new RuntimeException("Error generico");

        // When
        IngestionClient clienteFallback = fallbackFactory.create(causa);
        byte[] resultado = clienteFallback.obtenerBytes(77L);

        // Then — new byte[0] nunca es null
        assertNotNull(resultado);
        assertEquals(0, resultado.length);
        assertDoesNotThrow(() -> {
            @SuppressWarnings("unused")
            int len = resultado.length;
        });
    }
}
