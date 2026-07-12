package com.silvio.elending.client;

import com.silvio.elending.dto.SuscripcionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests para SubscriptionClientFallbackFactory
// Verifica que cuando el circuito está abierto o subscription-service no responde,
// se aplique el plan BASICO por defecto (2 préstamos, 7 días).
class SubscriptionClientFallbackFactoryTest {

    private SubscriptionClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new SubscriptionClientFallbackFactory();
    }

    @Test
    void create_conExcepcion_retornaSuscripcionBasico() {
        // Given — cualquier excepción que active el CB
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        SubscriptionClient clienteFallback = fallbackFactory.create(causa);
        SuscripcionDTO resultado = clienteFallback.obtenerSuscripcion("usuario123");

        // Then — suscripción degradada con plan BASICO (2 préstamos, 7 días, activa)
        assertNotNull(resultado);
        assertEquals("usuario123", resultado.getUsuarioId());
        assertEquals("BASICO", resultado.getPlan());
        assertEquals(2, resultado.getMaxPrestamos());
        assertEquals(7, resultado.getDiasPrestamo());
        assertTrue(resultado.getActiva());
    }

    @Test
    void create_conExcepcion_retornaSuscripcionSiempreActiva() {
        // Given — la suscripción degradada debe estar activa para permitir préstamos
        RuntimeException causa = new RuntimeException("Timeout");

        // When
        SubscriptionClient clienteFallback = fallbackFactory.create(causa);
        SuscripcionDTO resultado = clienteFallback.obtenerSuscripcion("user2");

        // Then — activa=true para no bloquear préstamos cuando subscription-service falla
        assertTrue(resultado.getActiva());
    }

    @Test
    void create_conExcepcion_yUsuarioIdNull_retornaFallbackConUsuarioIdNull() {
        // Given — caso borde: usuarioId null
        RuntimeException causa = new RuntimeException("Service unavailable");

        // When
        SubscriptionClient clienteFallback = fallbackFactory.create(causa);
        SuscripcionDTO resultado = clienteFallback.obtenerSuscripcion(null);

        // Then — no debe lanzar NPE, preserva el valor recibido
        assertNotNull(resultado);
        assertNull(resultado.getUsuarioId());
        assertEquals("BASICO", resultado.getPlan());
        assertEquals(2, resultado.getMaxPrestamos());
        assertEquals(7, resultado.getDiasPrestamo());
    }

    @Test
    void create_conExcepcionConNullMessage_retornaFallbackSinNPE() {
        // Given — excepción con mensaje null (caso borde)
        RuntimeException causa = new RuntimeException();

        // When
        SubscriptionClient clienteFallback = fallbackFactory.create(causa);
        SuscripcionDTO resultado = clienteFallback.obtenerSuscripcion("user_null_msg");

        // Then — debe funcionar sin NPE
        assertNotNull(resultado);
        assertEquals("user_null_msg", resultado.getUsuarioId());
        assertEquals("BASICO", resultado.getPlan());
    }

    @Test
    void create_conExcepcion_retornaSuscripcionConsistente() {
        // Given — múltiples llamadas al mismo fallback
        RuntimeException causa = new RuntimeException("connect timed out");
        SubscriptionClient clienteFallback = fallbackFactory.create(causa);

        // When — llamar varias veces
        SuscripcionDTO r1 = clienteFallback.obtenerSuscripcion("user_a");
        SuscripcionDTO r2 = clienteFallback.obtenerSuscripcion("user_b");

        // Then — ambas devuelven plan BASICO con diferentes usuarioId
        assertEquals("BASICO", r1.getPlan());
        assertEquals(2, r1.getMaxPrestamos());
        assertEquals(7, r1.getDiasPrestamo());
        assertTrue(r1.getActiva());
        assertEquals("user_a", r1.getUsuarioId());

        assertEquals("BASICO", r2.getPlan());
        assertEquals("user_b", r2.getUsuarioId());
    }

    @Test
    void create_conExcepcion_valoresNumericosCorrectos() {
        // Given — verifica una invariante importante: los valores por defecto
        RuntimeException causa = new RuntimeException("Internal server error");

        // When
        SubscriptionClient clienteFallback = fallbackFactory.create(causa);
        SuscripcionDTO resultado = clienteFallback.obtenerSuscripcion("invarian");

        // Then — maxPrestamos=2 y diasPrestamo=7 exactamente (valores del plan BASICO)
        assertEquals(2, resultado.getMaxPrestamos().intValue());
        assertEquals(7, resultado.getDiasPrestamo().intValue());
        // Verificar que no son null (importante para evitar NPE por auto-unboxing)
        assertNotNull(resultado.getMaxPrestamos());
        assertNotNull(resultado.getDiasPrestamo());
    }
}
