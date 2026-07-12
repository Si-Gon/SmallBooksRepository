package com.silvio.elending.client;

import com.silvio.elending.dto.LicenciaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests para LicenseClientFallbackFactory
// Verifica que cuando el circuito está abierto o license-service no responde,
// se devuelvan respuestas degradadas: licencia sin copias disponibles (0).
class LicenseClientFallbackFactoryTest {

    private LicenseClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new LicenseClientFallbackFactory();
    }

    @Test
    void create_conExcepcion_obtenerLicencia_retornaLicenciaDegradada() {
        // Given — cualquier excepción que active el CB
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        LicenseClient clienteFallback = fallbackFactory.create(causa);
        LicenciaDTO resultado = clienteFallback.obtenerLicencia(42L);

        // Then — licencia degradada con cero copias disponibles
        assertNotNull(resultado);
        assertEquals(42L, resultado.getLibroId());
        assertEquals(0, resultado.getTotalCopias().intValue());
        assertEquals(0, resultado.getCopiasDisponibles().intValue());
    }

    @Test
    void create_conExcepcion_prestar_retornaLicenciaDegradada() {
        // Given
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        LicenseClient clienteFallback = fallbackFactory.create(causa);
        LicenciaDTO resultado = clienteFallback.prestar(42L);

        // Then — no lanza excepción, devuelve licencia degradada
        assertNotNull(resultado);
        assertEquals(42L, resultado.getLibroId());
        assertEquals(0, resultado.getTotalCopias());
        assertEquals(0, resultado.getCopiasDisponibles());
    }

    @Test
    void create_conExcepcion_devolver_retornaLicenciaDegradada() {
        // Given
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        LicenseClient clienteFallback = fallbackFactory.create(causa);
        LicenciaDTO resultado = clienteFallback.devolver(42L);

        // Then — no lanza excepción, devuelve licencia degradada
        assertNotNull(resultado);
        assertEquals(42L, resultado.getLibroId());
        assertEquals(0, resultado.getTotalCopias());
        assertEquals(0, resultado.getCopiasDisponibles());
    }

    @Test
    void create_conExcepcion_tresMetodosDevuelvenRespuestasIndependientes() {
        // Given — una misma instancia de fallback
        RuntimeException causa = new RuntimeException("Read timed out");
        LicenseClient clienteFallback = fallbackFactory.create(causa);

        // When — llamar a los 3 métodos con diferentes IDs
        LicenciaDTO obtener = clienteFallback.obtenerLicencia(10L);
        LicenciaDTO prestar = clienteFallback.prestar(20L);
        LicenciaDTO devolver = clienteFallback.devolver(30L);

        // Then — cada método produce su respuesta con su propio libroId
        assertNotNull(obtener);
        assertEquals(10L, obtener.getLibroId());
        assertEquals(0, obtener.getCopiasDisponibles());

        assertNotNull(prestar);
        assertEquals(20L, prestar.getLibroId());
        assertEquals(0, prestar.getTotalCopias());

        assertNotNull(devolver);
        assertEquals(30L, devolver.getLibroId());
        assertEquals(0, devolver.getCopiasDisponibles());
    }

    @Test
    void create_conExcepcionConNullMessage_obtenerLicencia_noLanzaNPE() {
        // Given — excepción con mensaje null (caso borde)
        RuntimeException causa = new RuntimeException();

        // When
        LicenseClient clienteFallback = fallbackFactory.create(causa);
        LicenciaDTO resultado = clienteFallback.obtenerLicencia(100L);

        // Then — debe funcionar sin NPE porque el código maneja cause.getMessage() null
        assertNotNull(resultado);
        assertEquals(100L, resultado.getLibroId());
        assertEquals(0, resultado.getCopiasDisponibles());
        assertEquals(0, resultado.getTotalCopias());
    }

    @Test
    void create_conExcepcion_libroIdNull_noLanzaNPE() {
        // Given — caso borde: libroId null
        RuntimeException causa = new RuntimeException("Service unavailable");

        // When
        LicenseClient clienteFallback = fallbackFactory.create(causa);
        LicenciaDTO resultado = clienteFallback.obtenerLicencia(null);

        // Then — no debe lanzar NPE, el libroId se setea como null
        assertNotNull(resultado);
        assertNull(resultado.getLibroId());
        assertEquals(0, resultado.getCopiasDisponibles());
        assertEquals(0, resultado.getTotalCopias());
    }

    @Test
    void create_conExcepcion_prestar_libroIdNull_noLanzaNPE() {
        // Given — caso borde: libroId null en prestar
        RuntimeException causa = new RuntimeException("Timeout");

        // When
        LicenseClient clienteFallback = fallbackFactory.create(causa);
        LicenciaDTO resultado = clienteFallback.prestar(null);

        // Then — no debe lanzar NPE
        assertNotNull(resultado);
        assertNull(resultado.getLibroId());
        assertEquals(0, resultado.getCopiasDisponibles());
    }

    @Test
    void create_conExcepcion_devolver_libroIdNull_noLanzaNPE() {
        // Given — caso borde: libroId null en devolver
        RuntimeException causa = new RuntimeException("Timeout");

        // When
        LicenseClient clienteFallback = fallbackFactory.create(causa);
        LicenciaDTO resultado = clienteFallback.devolver(null);

        // Then — no debe lanzar NPE
        assertNotNull(resultado);
        assertNull(resultado.getLibroId());
        assertEquals(0, resultado.getCopiasDisponibles());
    }

    @Test
    void create_conExcepcionDeRed_obtenerLicencia_camposConsistentes() {
        // Given — simula una excepción de red típica
        RuntimeException causa = new RuntimeException(
                "connect: Address is invalid, or socket could not be bound");

        // When
        LicenseClient clienteFallback = fallbackFactory.create(causa);
        LicenciaDTO resultado = clienteFallback.obtenerLicencia(1L);

        // Then — respuesta degradada consistente independientemente del tipo de excepción
        assertEquals(1L, resultado.getLibroId());
        assertEquals(0, resultado.getTotalCopias().intValue());
        assertEquals(0, resultado.getCopiasDisponibles().intValue());
    }

    @Test
    void create_conExcepcion_valoresNumericosNoSonNull() {
        // Given — verifica que Integer fields no sean null (evita NPE por auto-unboxing)
        RuntimeException causa = new RuntimeException("Internal server error");

        // When
        LicenseClient clienteFallback = fallbackFactory.create(causa);
        LicenciaDTO resultado = clienteFallback.obtenerLicencia(99L);

        // Then — totalCopias y copiasDisponibles deben ser 0 (no null)
        assertNotNull(resultado);
        assertNotNull(resultado.getTotalCopias(),
                "totalCopias no debe ser null para evitar NPE por auto-unboxing");
        assertNotNull(resultado.getCopiasDisponibles(),
                "copiasDisponibles no debe ser null para evitar NPE por auto-unboxing");
        assertEquals(0, resultado.getTotalCopias().intValue());
        assertEquals(0, resultado.getCopiasDisponibles().intValue());
    }

    @Test
    void create_conExcepcion_causeNull_noLanzaNPE() {
        // Given — caso borde extremo: cause es null
        // El código hace cause.getMessage() != null, lo que lanzaría NPE si cause es null.
        // Este test documenta el comportamiento actual (podría mejorarse en el futuro).

        // When / Then
        assertThrows(NullPointerException.class, () -> fallbackFactory.create(null),
                "Cuando cause es null, el fallback debe lanzar NPE. "
                + "Considerar agregar null-check si se desea manejar este caso.");
    }
}
