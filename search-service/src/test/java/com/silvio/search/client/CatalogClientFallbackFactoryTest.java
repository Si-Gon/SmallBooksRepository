package com.silvio.search.client;

import com.silvio.search.dto.LibroCatalogDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests para CatalogClientFallbackFactory
// Verifica que cuando el circuito esta abierto o catalog-service no responde,
// se devuelvan respuestas degradadas: pagina vacia para obtenerTodos
// y lista vacia para buscar y obtenerDisponibles.
class CatalogClientFallbackFactoryTest {

    private CatalogClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new CatalogClientFallbackFactory();
    }

    @Test
    void create_conExcepcion_obtenerTodos_retornaPaginaVacia() {
        // Given — cualquier excepcion que active el CB
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        Page<LibroCatalogDTO> resultado = clienteFallback.obtenerTodos(0, 20, "titulo,asc");

        // Then — pagina vacia, no null
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_buscar_retornaListaVacia() {
        // Given
        RuntimeException causa = new RuntimeException("Timeout");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        List<LibroCatalogDTO> resultado = clienteFallback.buscar("titulo", "autor", "genero");

        // Then — lista vacia, no null
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerDisponibles_retornaListaVacia() {
        // Given
        RuntimeException causa = new RuntimeException("Service unavailable");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        List<LibroCatalogDTO> resultado = clienteFallback.obtenerDisponibles();

        // Then — lista vacia, no null
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcionConNullMessage_obtenerTodos_retornaPaginaVacia() {
        // Given — excepcion con mensaje null (caso borde)
        RuntimeException causa = new RuntimeException();

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        Page<LibroCatalogDTO> resultado = clienteFallback.obtenerTodos(0, 20, "titulo,asc");

        // Then — debe funcionar sin NPE
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcionConNullMessage_buscar_retornaListaVacia() {
        // Given — excepcion con mensaje null
        RuntimeException causa = new RuntimeException();

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        List<LibroCatalogDTO> resultado = clienteFallback.buscar("test", null, null);

        // Then — debe funcionar sin NPE
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_parametrosBusquedaNull_retornaListaVacia() {
        // Given — todos los parametros de busqueda null (caso borde)
        RuntimeException causa = new RuntimeException("Error generico");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        List<LibroCatalogDTO> resultado = clienteFallback.buscar(null, null, null);

        // Then — no debe lanzar NPE
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_parametrosBusquedaVacios_retornaListaVacia() {
        // Given
        RuntimeException causa = new RuntimeException("Error");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        List<LibroCatalogDTO> resultado = clienteFallback.buscar("", "", "");

        // Then
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerTodos_conParametrosVariados_retornaPaginaVaciaSiempre() {
        // Given
        RuntimeException causa = new RuntimeException("Error generico");
        CatalogClient clienteFallback = fallbackFactory.create(causa);

        // When & Then — diferentes combinaciones page/size/sort siempre retornan Page.empty()
        assertTrue(clienteFallback.obtenerTodos(0, 1, "id,asc").isEmpty());
        assertTrue(clienteFallback.obtenerTodos(5, 50, "autor,desc").isEmpty());
        assertTrue(clienteFallback.obtenerTodos(999, 999, "campoInexistente,asc").isEmpty());
        assertTrue(clienteFallback.obtenerTodos(0, Integer.MAX_VALUE, "").isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerTodos_pageNegativa_retornaPaginaVacia() {
        // Given
        RuntimeException causa = new RuntimeException("Error");
        CatalogClient clienteFallback = fallbackFactory.create(causa);

        // When — pagina negativa
        Page<LibroCatalogDTO> resultado = clienteFallback.obtenerTodos(-1, 20, "titulo,asc");

        // Then — debe retornar vacio sin lanzar excepcion
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerTodos_sortInvalido_retornaPaginaVacia() {
        // Given
        RuntimeException causa = new RuntimeException("Error");
        CatalogClient clienteFallback = fallbackFactory.create(causa);

        // When — sort con formato invalido
        Page<LibroCatalogDTO> resultado = clienteFallback.obtenerTodos(0, 20, ",,");

        // Then — debe retornar vacio sin lanzar excepcion
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerTodos_parametrosDefault_retornaPaginaVacia() {
        // Given — simula que catalog-service no responde
        RuntimeException causa = new RuntimeException("Connection refused");
        CatalogClient clienteFallback = fallbackFactory.create(causa);

        // When — valores por defecto del @RequestParam en el Feign client
        Page<LibroCatalogDTO> resultado = clienteFallback.obtenerTodos(0, 20, "titulo,asc");

        // Then — la pagina vacia mantiene las propiedades de Page.empty()
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
        assertFalse(resultado.hasContent());
        assertEquals(0, resultado.getNumber());
        assertEquals(0, resultado.getTotalElements());
    }

    @Test
    void fallback_buscar_retornaListaInmutable() {
        // Given
        RuntimeException causa = new RuntimeException("Error");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        List<LibroCatalogDTO> resultado = clienteFallback.buscar("test", null, null);

        // Then — Collections.emptyList() retorna una lista inmutable
        assertNotNull(resultado);
        assertThrows(UnsupportedOperationException.class,
                () -> resultado.add(new LibroCatalogDTO()));
    }

    @Test
    void fallback_obtenerDisponibles_retornaListaInmutable() {
        // Given
        RuntimeException causa = new RuntimeException("Error");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);
        List<LibroCatalogDTO> resultado = clienteFallback.obtenerDisponibles();

        // Then — Collections.emptyList() retorna una lista inmutable
        assertNotNull(resultado);
        assertThrows(UnsupportedOperationException.class,
                () -> resultado.add(new LibroCatalogDTO()));
    }

    @Test
    void create_conExcepcionDeRed_retornaRespuestasConsistentes() {
        // Given — simula una excepcion tipica de red
        RuntimeException causa = new RuntimeException(
                "connect: Address is invalid, or socket could not be bound");

        // When
        CatalogClient clienteFallback = fallbackFactory.create(causa);

        // Then — todos los metodos producen respuestas degradadas consistentes
        assertTrue(clienteFallback.obtenerTodos(0, 20, "titulo,asc").isEmpty());
        assertTrue(clienteFallback.buscar("test", null, null).isEmpty());
        assertTrue(clienteFallback.obtenerDisponibles().isEmpty());
    }

    @Test
    void create_conExcepcion_tresMetodos_independientes() {
        // Given — una misma instancia de fallback
        RuntimeException causa = new RuntimeException("Read timed out");
        CatalogClient clienteFallback = fallbackFactory.create(causa);

        // When — llamar a los tres metodos
        Page<LibroCatalogDTO> pagina = clienteFallback.obtenerTodos(0, 20, "titulo,asc");
        List<LibroCatalogDTO> busqueda = clienteFallback.buscar("java", null, null);
        List<LibroCatalogDTO> disponibles = clienteFallback.obtenerDisponibles();

        // Then — cada metodo produce su respuesta degradada independiente
        assertNotNull(pagina);
        assertTrue(pagina.isEmpty());

        assertNotNull(busqueda);
        assertTrue(busqueda.isEmpty());

        assertNotNull(disponibles);
        assertTrue(disponibles.isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerDisponibles_llamadasMultiples_retornaListaVaciaSiempre() {
        // Given
        RuntimeException causa = new RuntimeException("Error");
        CatalogClient clienteFallback = fallbackFactory.create(causa);

        // When & Then — multiples llamadas siempre retornan lista vacia
        assertTrue(clienteFallback.obtenerDisponibles().isEmpty());
        assertTrue(clienteFallback.obtenerDisponibles().isEmpty());
        assertTrue(clienteFallback.obtenerDisponibles().isEmpty());
    }
}
