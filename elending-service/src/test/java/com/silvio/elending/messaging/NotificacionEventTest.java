package com.silvio.elending.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de casos borde adicionales para los factory methods de NotificacionEvent.
 *
 * Complementa los tests existentes en NotificacionRequestDTOTest cubriendo
 * strings vacíos, IDs negativos, caracteres especiales y el constructor vacío
 * (necesario para deserialización JSON en el consumidor).
 */
class NotificacionEventTest {

    // ─── prestamoCreado — casos borde de string ──────────────────────────────

    @Test
    void prestamoCreado_conUsuarioIdVacio_debeCrearEvento() {
        // When — usuarioId vacío (borde de @Size en el DTO del consumidor)
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("", 1L, 7);

        // Then
        assertNotNull(evento);
        assertEquals("", evento.getUsuarioId());
        assertEquals("PRESTAMO_CREADO", evento.getTipo());
    }

    @Test
    void prestamoCreado_conUsuarioIdMuyLargo_debeCrearEvento() {
        // Given — string de 255 caracteres (borde superior del campo en BD)
        String largo = "a".repeat(255);
        NotificacionEvent evento = NotificacionEvent.prestamoCreado(largo, 1L, 7);

        // Then
        assertNotNull(evento);
        assertEquals(largo, evento.getUsuarioId());
        assertTrue(evento.getMensaje().contains("7 días"));
    }

    @Test
    void prestamoCreado_conLibroIdCero_debeCrearEvento() {
        // When — libroId = 0 (no debería ocurrir, pero debe manejarse)
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("user", 0L, 7);

        // Then — el libroId 0 se incluye en el mensaje como string
        assertNotNull(evento);
        assertEquals("user", evento.getUsuarioId());
        assertTrue(evento.getMensaje().contains("0"));
        assertTrue(evento.getMensaje().contains("7 días"));
    }

    @Test
    void prestamoCreado_conLibroIdNegativo_debeCrearEvento() {
        // When — libroId negativo (caso borde extremo de Long)
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("user", -1L, 7);

        // Then
        assertNotNull(evento);
        assertTrue(evento.getMensaje().contains("-1"));
        assertTrue(evento.getMensaje().contains("7 días"));
    }

    // ─── prestamoVencido — casos borde ───────────────────────────────────────

    @Test
    void prestamoVencido_conUsuarioIdVacio_debeCrearEvento() {
        // When
        NotificacionEvent evento = NotificacionEvent.prestamoVencido("", 99L);

        // Then
        assertNotNull(evento);
        assertEquals("", evento.getUsuarioId());
        assertEquals("VENCIDO", evento.getTipo());
    }

    @Test
    void prestamoVencido_conLibroIdMaxValue_debeCrearEvento() {
        // When — Long.MAX_VALUE
        NotificacionEvent evento = NotificacionEvent.prestamoVencido("user", Long.MAX_VALUE);

        // Then
        assertNotNull(evento);
        assertTrue(evento.getMensaje().contains(String.valueOf(Long.MAX_VALUE)));
        assertTrue(evento.getMensaje().contains("vencido"));
    }

    // ─── proximoVencer — casos borde ─────────────────────────────────────────

    @Test
    void proximoVencer_conCaracteresEspecialesEnUsuarioId_debeCrearEvento() {
        // Given — usuarioId con caracteres especiales y espacios
        String especial = "usuario@test#123 + ñ";
        NotificacionEvent evento = NotificacionEvent.proximoVencer(especial, 7L);

        // Then
        assertNotNull(evento);
        assertEquals(especial, evento.getUsuarioId());
        assertEquals("PROXIMO_VENCER", evento.getTipo());
        assertTrue(evento.getMensaje().contains("2 días"));
    }

    @Test
    void proximoVencer_conUsuarioIdUnicode_debeCrearEvento() {
        // Given — usuarioId con caracteres Unicode
        String unicode = "usuario_测试_ユーザー";
        NotificacionEvent evento = NotificacionEvent.proximoVencer(unicode, 7L);

        // Then
        assertNotNull(evento);
        assertEquals(unicode, evento.getUsuarioId());
        assertTrue(evento.getMensaje().contains("vence en 2 días"));
    }

    // ─── constructor vacío y setters (deserialización JSON) ───────────────────

    @Test
    void constructorSinArgsYsetters_debenFuncionar() {
        // Verifica que el constructor vacío + setters funciona para Jackson
        // Given
        NotificacionEvent evento = new NotificacionEvent();

        // When
        evento.setUsuarioId("json_user");
        evento.setTipo("PRESTAMO_CREADO");
        evento.setMensaje("Mensaje desde JSON");

        // Then
        assertEquals("json_user", evento.getUsuarioId());
        assertEquals("PRESTAMO_CREADO", evento.getTipo());
        assertEquals("Mensaje desde JSON", evento.getMensaje());
    }

    @Test
    void equalsYhashCode_debenFuncionar() {
        // Verifica que @Data genera equals/hashCode correctamente
        NotificacionEvent e1 = new NotificacionEvent("u1", "PRESTAMO_CREADO", "Msg");
        NotificacionEvent e2 = new NotificacionEvent("u1", "PRESTAMO_CREADO", "Msg");
        NotificacionEvent e3 = new NotificacionEvent("u2", "VENCIDO", "Otro");

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertNotEquals(e1, e3);
    }

    // ─── diasPrestamo — casos borde (<= 0 debe asignar 7 por defecto) ──────────

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
        // When — diasPrestamo = -5 (inválido), debe asignar 7 por defecto
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("test_user", 1L, -5);

        // Then
        assertNotNull(evento);
        assertEquals("PRESTAMO_CREADO", evento.getTipo());
        assertTrue(evento.getMensaje().contains("7 días"),
                "Con diasPrestamo=-5 el mensaje debe decir '7 días'");
        assertFalse(evento.getMensaje().contains("-5"),
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
        assertFalse(evento.getMensaje().contains("-2147483648"),
                "El mensaje no debe contener el valor MIN_VALUE original");
    }

    // ─── verificación de mensajes con espacios y formato ──────────────────────

    @Test
    void prestamoCreado_mensajeDebeTenerFormatoLegible() {
        // Verifica que el mensaje generado sea legible y contenga todos los datos
        NotificacionEvent evento = NotificacionEvent.prestamoCreado("usuario_test", 123L, 14);

        String msg = evento.getMensaje();
        assertTrue(msg.contains("123"), "Debe contener el ID del libro");
        assertTrue(msg.contains("14 días"), "Debe contener la duración");
        assertTrue(msg.contains("creado exitosamente"), "Debe indicar éxito");
        // El formato debe ser natural y legible
        assertFalse(msg.contains("null"), "No debe contener 'null'");
    }

    @Test
    void prestamoVencido_mensajeDebeMencionarCierreAutomatico() {
        NotificacionEvent evento = NotificacionEvent.prestamoVencido("user", 456L);

        String msg = evento.getMensaje();
        assertTrue(msg.contains("456"));
        assertTrue(msg.contains("vencido"));
        assertTrue(msg.contains("cerrado automáticamente"));
    }

    @Test
    void proximoVencer_mensajeDebeMencionarTiempoRestante() {
        NotificacionEvent evento = NotificacionEvent.proximoVencer("user", 789L);

        String msg = evento.getMensaje();
        assertTrue(msg.contains("789"));
        assertTrue(msg.contains("vence en 2 días"));
        assertTrue(msg.contains("tiempo restante"));
    }
}
