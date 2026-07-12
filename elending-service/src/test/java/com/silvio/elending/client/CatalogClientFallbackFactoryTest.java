package com.silvio.elending.client;

import com.silvio.elending.dto.LibroDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests para CatalogClientFallbackFactory
// Verifica que cuando el circuito está abierto o catalog-service no responde,
// se devuelvan respuestas degradadas: libro "No disponible" y lista vacía.
class CatalogClientFallbackFactoryTest {

    private CatalogClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new CatalogClientFallbackFactory();
    }

    @Test
    void create_conExcepcion_obtenerLibro_retornaLibroNoDisponible() {
        // Given — cualquier excepción que active el CB
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        LibroDTO resultado = clienteFallback.obtenerLibro(42L);

        // Then — libro degradado con todos los campos "No disponible"
        assertNotNull(resultado);
        assertEquals(42L, resultado.getId());
        assertEquals("No disponible", resultado.getTitulo());
        assertEquals("No disponible", resultado.getAutor());
        assertEquals("No disponible", resultado.getIsbn());
        assertEquals("No disponible", resultado.getGenero());
        assertFalse(resultado.getDisponible());
    }

    @Test
    void create_conExcepcion_obtenerTodos_retornaListaVacia() {
        // Given
        RuntimeException causa = new RuntimeException("Timeout");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        List<LibroDTO> resultado = clienteFallback.obtenerTodos();

        // Then — lista vacía, no null
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerLibro_conIdNull_retornaLibroNoDisponible() {
        // Given — caso borde: id null
        RuntimeException causa = new RuntimeException("Service unavailable");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        LibroDTO resultado = clienteFallback.obtenerLibro(null);

        // Then — no debe lanzar NPE, el id se setea como null
        assertNotNull(resultado);
        assertNull(resultado.getId());
        assertEquals("No disponible", resultado.getTitulo());
        assertFalse(resultado.getDisponible());
    }

    @Test
    void create_conExcepcionConNullMessage_obtenerLibro_funciona() {
        // Given — excepción con mensaje null (caso borde)
        RuntimeException causa = new RuntimeException();

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        LibroDTO resultado = clienteFallback.obtenerLibro(100L);

        // Then — debe funcionar sin NPE
        assertNotNull(resultado);
        assertEquals(100L, resultado.getId());
        assertEquals("No disponible", resultado.getTitulo());
    }

    @Test
    void create_conExcepcion_obtenerLibro_y_obtenerTodos_independientes() {
        // Given — una misma instancia de fallback
        RuntimeException causa = new RuntimeException("Read timed out");
        CatalogClient clienteFallback = fallbackFactory.create(causa);

        // When — llamar a ambos métodos
        LibroDTO libro = clienteFallback.obtenerLibro(77L);
        List<LibroDTO> lista = clienteFallback.obtenerTodos();

        // Then — cada método produce su respuesta degradada independiente
        assertNotNull(libro);
        assertEquals(77L, libro.getId());
        assertEquals("No disponible", libro.getTitulo());

        assertNotNull(lista);
        assertTrue(lista.isEmpty());
    }

    @Test
    void create_conExcepcionDeRed_obtenerLibro_camposConsistentes() {
        // Given — simula una excepción de red típica
        RuntimeException causa = new RuntimeException(
                "connect: Address is invalid, or socket could not be bound");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        LibroDTO resultado = clienteFallback.obtenerLibro(1L);

        // Then — todos los campos de texto son exactamente "No disponible"
        assertEquals("No disponible", resultado.getTitulo());
        assertEquals("No disponible", resultado.getAutor());
        assertEquals("No disponible", resultado.getIsbn());
        assertEquals("No disponible", resultado.getGenero());
        assertFalse(resultado.getDisponible());
    }
}
