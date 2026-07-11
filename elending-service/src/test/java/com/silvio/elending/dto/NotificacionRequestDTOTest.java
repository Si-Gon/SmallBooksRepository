package com.silvio.elending.dto;

import com.silvio.elending.messaging.NotificacionEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de los factory methods estáticos de NotificacionEvent.
 *
 * Verifica que los métodos prestamoCreado(), prestamoVencido() y proximoVencer()
 * construyan correctamente el evento con los valores esperados.
 */
class NotificacionRequestDTOTest {

    @Test
    void prestamoCreado_debeCrearDTOConDatosCorrectos() {
        // When — plan BASICO (7 días)
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("usuario1", 42L, 7);

        // Then
        assertNotNull(evento);
        assertEquals("usuario1", evento.getUsuarioId());
        assertEquals("PRESTAMO_CREADO", evento.getTipo());
        assertTrue(evento.getMensaje().contains("42"));
        assertTrue(evento.getMensaje().contains("creado"));
        assertTrue(evento.getMensaje().contains("7 días"));
    }

    @Test
    void prestamoVencido_debeCrearDTOConDatosCorrectos() {
        // When
        NotificacionEvent evento = NotificacionEvent.prestamoVencido("usuario2", 99L);

        // Then
        assertNotNull(evento);
        assertEquals("usuario2", evento.getUsuarioId());
        assertEquals("VENCIDO", evento.getTipo());
        assertTrue(evento.getMensaje().contains("99"));
        assertTrue(evento.getMensaje().contains("vencido"));
    }

    @Test
    void proximoVencer_debeCrearDTOConDatosCorrectos() {
        // When
        NotificacionEvent evento = NotificacionEvent.proximoVencer("usuario3", 7L);

        // Then
        assertNotNull(evento);
        assertEquals("usuario3", evento.getUsuarioId());
        assertEquals("PROXIMO_VENCER", evento.getTipo());
        assertTrue(evento.getMensaje().contains("7"));
        assertTrue(evento.getMensaje().contains("2 días"));
    }

    @Test
    void prestamoCreado_conLongitudMaximaEnIds_debeFuncionar() {
        // When — IDs grandes (borde de Long) con plan PREMIUM (14 días)
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("user_muy_largo_12345", Long.MAX_VALUE, 14);

        // Then
        assertNotNull(evento);
        assertEquals("user_muy_largo_12345", evento.getUsuarioId());
        assertTrue(evento.getMensaje().contains(String.valueOf(Long.MAX_VALUE)));
        assertTrue(evento.getMensaje().contains("14 días"));
    }

    // ─── tests validación diasPrestamo <= 0 ──────────────────────────────────

    @Test
    void prestamoCreado_conDiasCero_asigna7PorDefecto() {
        // When — diasPrestamo = 0 (inválido), debe asignar 7 por defecto
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("test_user", 1L, 0);

        // Then
        assertNotNull(evento);
        assertEquals("PRESTAMO_CREADO", evento.getTipo());
        assertTrue(evento.getMensaje().contains("7 días"),
                "Con diasPrestamo=0 el mensaje debe decir '7 días', no '0 días'");
        assertFalse(evento.getMensaje().contains("0 días"),
                "El mensaje no debe contener '0 días'");
    }

    @Test
    void prestamoCreado_conDiasNegativo_asigna7PorDefecto() {
        // When — diasPrestamo = -1 (inválido), debe asignar 7 por defecto
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("test_user", 1L, -1);

        // Then
        assertNotNull(evento);
        assertEquals("PRESTAMO_CREADO", evento.getTipo());
        assertTrue(evento.getMensaje().contains("7 días"),
                "Con diasPrestamo=-1 el mensaje debe decir '7 días'");
        assertFalse(evento.getMensaje().contains("-1"),
                "El mensaje no debe contener el valor negativo original");
    }

    @Test
    void prestamoCreado_conDiasMinValue_asigna7PorDefecto() {
        // When — diasPrestamo = Integer.MIN_VALUE (caso borde extremo)
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("test_user", 1L, Integer.MIN_VALUE);

        // Then
        assertNotNull(evento);
        assertEquals("PRESTAMO_CREADO", evento.getTipo());
        assertTrue(evento.getMensaje().contains("7 días"),
                "Con Integer.MIN_VALUE el mensaje debe decir '7 días'");
        // El mensaje debe ser coherente incluso con el valor mínimo de Integer
        assertFalse(evento.getMensaje().contains("-2147483648"),
                "El mensaje no debe contener el valor MIN_VALUE original");
    }
}
