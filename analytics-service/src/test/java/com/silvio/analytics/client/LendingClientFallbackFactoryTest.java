package com.silvio.analytics.client;

import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests para LendingClientFallbackFactory
// Verifica que cuando el circuito esta abierto o elending-service no responde,
// se devuelvan respuestas degradadas: pagina vacia para obtenerTodos
// y lista vacia para obtenerHistorial.
class LendingClientFallbackFactoryTest {

    private LendingClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new LendingClientFallbackFactory();
    }

    @Test
    void create_conExcepcion_obtenerTodos_retornaPaginaVacia() {
        // Given — cualquier excepcion que active el CB
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        Page<PrestamoAnalyticsDTO> resultado = clienteFallback.obtenerTodos();

        // Then — pagina vacia, no null
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerHistorial_retornaListaVacia() {
        // Given
        RuntimeException causa = new RuntimeException("Timeout");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoAnalyticsDTO> resultado = clienteFallback.obtenerHistorial("user123");

        // Then — lista vacia, no null
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcionConNullMessage_obtenerTodos_retornaPaginaVacia() {
        // Given — excepcion con mensaje null (caso borde)
        RuntimeException causa = new RuntimeException();

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        Page<PrestamoAnalyticsDTO> resultado = clienteFallback.obtenerTodos();

        // Then — debe funcionar sin NPE
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcionConNullMessage_obtenerHistorial_retornaListaVacia() {
        // Given — excepcion con mensaje null
        RuntimeException causa = new RuntimeException();

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoAnalyticsDTO> resultado = clienteFallback.obtenerHistorial("user456");

        // Then — debe funcionar sin NPE
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_usuarioIdNull_obtenerHistorial_retornaListaVacia() {
        // Given — usuarioId null (caso borde)
        RuntimeException causa = new RuntimeException("Service unavailable");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoAnalyticsDTO> resultado = clienteFallback.obtenerHistorial(null);

        // Then — no debe lanzar NPE
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void create_conExcepcion_usuarioIdVacio_obtenerHistorial_retornaListaVacia() {
        // Given
        RuntimeException causa = new RuntimeException("Error generico");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoAnalyticsDTO> resultado = clienteFallback.obtenerHistorial("");

        // Then
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void fallback_obtenerTodos_retornaPaginaSinContenido() {
        // Given
        RuntimeException causa = new RuntimeException("Read timed out");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        Page<PrestamoAnalyticsDTO> resultado = clienteFallback.obtenerTodos();

        // Then — propiedades de Page.empty()
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
        assertFalse(resultado.hasContent());
        assertEquals(0, resultado.getNumber());
        assertEquals(0, resultado.getTotalElements());
    }

    @Test
    void fallback_obtenerHistorial_retornaListaInmutable() {
        // Given
        RuntimeException causa = new RuntimeException("Error");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);
        List<PrestamoAnalyticsDTO> resultado = clienteFallback.obtenerHistorial("userX");

        // Then — Collections.emptyList() retorna una lista inmutable
        assertNotNull(resultado);
        assertThrows(UnsupportedOperationException.class,
                () -> resultado.add(new PrestamoAnalyticsDTO()));
    }

    @Test
    void create_conExcepcionDeRed_retornaRespuestasConsistentes() {
        // Given — simula una excepcion tipica de red
        RuntimeException causa = new RuntimeException(
                "connect: Address is invalid, or socket could not be bound");

        // When
        LendingClient clienteFallback = fallbackFactory.create(causa);

        // Then — ambos metodos producen respuestas degradadas consistentes
        assertTrue(clienteFallback.obtenerTodos().isEmpty());
        assertTrue(clienteFallback.obtenerHistorial("user_network").isEmpty());
    }

    @Test
    void create_conExcepcion_ambosMetodos_independientes() {
        // Given — una misma instancia de fallback
        RuntimeException causa = new RuntimeException("Connection reset");
        LendingClient clienteFallback = fallbackFactory.create(causa);

        // When — llamar a ambos metodos
        Page<PrestamoAnalyticsDTO> pagina = clienteFallback.obtenerTodos();
        List<PrestamoAnalyticsDTO> historial = clienteFallback.obtenerHistorial("user_indep");

        // Then — cada metodo produce su respuesta degradada independiente
        assertNotNull(pagina);
        assertTrue(pagina.isEmpty());

        assertNotNull(historial);
        assertTrue(historial.isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerTodos_llamadasMultiples_retornaPaginaVaciaSiempre() {
        // Given
        RuntimeException causa = new RuntimeException("Error");
        LendingClient clienteFallback = fallbackFactory.create(causa);

        // When & Then — multiples llamadas siempre retornan pagina vacia
        assertTrue(clienteFallback.obtenerTodos().isEmpty());
        assertTrue(clienteFallback.obtenerTodos().isEmpty());
        assertTrue(clienteFallback.obtenerTodos().isEmpty());
    }

    @Test
    void create_conExcepcion_obtenerHistorial_distintosUsuarios_retornaListaVaciaSiempre() {
        // Given
        RuntimeException causa = new RuntimeException("Error");
        LendingClient clienteFallback = fallbackFactory.create(causa);

        // When & Then — distintos usuarios siempre retornan lista vacia
        assertTrue(clienteFallback.obtenerHistorial("user1").isEmpty());
        assertTrue(clienteFallback.obtenerHistorial("user2").isEmpty());
        assertTrue(clienteFallback.obtenerHistorial("").isEmpty());
        assertTrue(clienteFallback.obtenerHistorial(null).isEmpty());
    }
}
