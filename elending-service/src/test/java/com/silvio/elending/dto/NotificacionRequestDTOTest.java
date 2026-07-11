package com.silvio.elending.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de los factory methods estáticos de NotificacionRequestDTO.
 *
 * Verifica que los métodos prestamoCreado(), prestamoVencido() y proximoVencer()
 * construyan correctamente el DTO con los valores esperados.
 */
class NotificacionRequestDTOTest {

    @Test
    void prestamoCreado_debeCrearDTOConDatosCorrectos() {
        // When — plan BASICO (7 días)
        NotificacionRequestDTO dto = NotificacionRequestDTO.prestamoCreado("usuario1", 42L, 7);

        // Then
        assertNotNull(dto);
        assertEquals("usuario1", dto.getUsuarioId());
        assertEquals("PRESTAMO_CREADO", dto.getTipo());
        assertTrue(dto.getMensaje().contains("42"));
        assertTrue(dto.getMensaje().contains("creado"));
        assertTrue(dto.getMensaje().contains("7 días"));
    }

    @Test
    void prestamoVencido_debeCrearDTOConDatosCorrectos() {
        // When
        NotificacionRequestDTO dto = NotificacionRequestDTO.prestamoVencido("usuario2", 99L);

        // Then
        assertNotNull(dto);
        assertEquals("usuario2", dto.getUsuarioId());
        assertEquals("VENCIDO", dto.getTipo());
        assertTrue(dto.getMensaje().contains("99"));
        assertTrue(dto.getMensaje().contains("vencido"));
    }

    @Test
    void proximoVencer_debeCrearDTOConDatosCorrectos() {
        // When
        NotificacionRequestDTO dto = NotificacionRequestDTO.proximoVencer("usuario3", 7L);

        // Then
        assertNotNull(dto);
        assertEquals("usuario3", dto.getUsuarioId());
        assertEquals("PROXIMO_VENCER", dto.getTipo());
        assertTrue(dto.getMensaje().contains("7"));
        assertTrue(dto.getMensaje().contains("2 días"));
    }

    @Test
    void prestamoCreado_conLongitudMaximaEnIds_debeFuncionar() {
        // When — IDs grandes (borde de Long) con plan PREMIUM (14 días)
        NotificacionRequestDTO dto = NotificacionRequestDTO.prestamoCreado("user_muy_largo_12345", Long.MAX_VALUE, 14);

        // Then
        assertNotNull(dto);
        assertEquals("user_muy_largo_12345", dto.getUsuarioId());
        assertTrue(dto.getMensaje().contains(String.valueOf(Long.MAX_VALUE)));
        assertTrue(dto.getMensaje().contains("14 días"));
    }
}
