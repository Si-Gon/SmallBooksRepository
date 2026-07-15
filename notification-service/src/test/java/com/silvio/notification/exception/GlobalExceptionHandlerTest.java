package com.silvio.notification.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del GlobalExceptionHandler del Notification Service.
 *
 * Verifica que cada excepción de dominio se mapee al HTTP status correcto.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // =========================================================
    // MethodArgumentNotValidException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void validacion_debeRetornar400ConErrores() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "email", "El email es obligatorio");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, String>> response = handler.manejarValidacion(ex);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("email"));
        assertEquals("El email es obligatorio", response.getBody().get("email"));
    }

    // =========================================================
    // NotificacionNotFoundException → 404 NOT_FOUND
    // =========================================================

    @Test
    void notificacionNoEncontrada_debeRetornar404() {
        ResponseEntity<Map<String, String>> response = handler.manejarNotificacionNoEncontrada(
                new NotificacionNotFoundException(99L));

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("99"));
    }

    // =========================================================
    // HashNoDisponibleException → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void hashNoDisponible_debeRetornar500() {
        ResponseEntity<Map<String, String>> response = handler.manejarHashNoDisponible(
                new HashNoDisponibleException(new RuntimeException("SHA-256 provider")));

        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("SHA-256"));
    }

    // =========================================================
    // RuntimeException → 500 INTERNAL_SERVER_ERROR (fallback)
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar500() {
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(
                new RuntimeException("Error inesperado en notification"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error interno del servidor", response.getBody().get("error"));
    }

    @Test
    void runtimeException_conMensajeNull_debeRetornar500() {
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(
                new RuntimeException((String) null));

        assertEquals(500, response.getStatusCode().value());
    }
}
