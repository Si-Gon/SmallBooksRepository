package com.silvio.elending.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del GlobalExceptionHandler del E-Lending Service.
 *
 * El handler mapea RuntimeException a diferentes HTTP status según el mensaje:
 * - "No hay copias" → 422 UNPROCESSABLE_ENTITY
 * - "límite"       → 422 UNPROCESSABLE_ENTITY
 * - "Ya tienes"    → 409 CONFLICT
 * - "No se pudo verificar" → 503 SERVICE_UNAVAILABLE
 * - Otros          → 400 BAD_REQUEST
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // =========================================================
    // MethodArgumentNotValidException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void validacion_debeRetornar400ConErrores() {
        // Given
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        var bindingResult = mock(org.springframework.validation.BindingResult.class);
        var fieldError = new org.springframework.validation.FieldError(
                "object", "libroId", "El ID del libro es obligatorio");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        // When
        var response = handler.manejarValidacion(ex);

        // Then
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("libroId"));
        assertEquals("El ID del libro es obligatorio", response.getBody().get("libroId"));
    }

    // =========================================================
    // RuntimeException → mapeo según mensaje
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar400() {
        var response = handler.manejarRuntimeException(
                new RuntimeException("Error genérico"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Error genérico", response.getBody().get("error"));
    }

    @Test
    void runtimeException_sinCopias_debeRetornar422() {
        var response = handler.manejarRuntimeException(
                new RuntimeException("No hay copias disponibles del libro con id: 1"));

        assertEquals(422, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("No hay copias"));
    }

    @Test
    void runtimeException_limiteAlcanzado_debeRetornar422() {
        var response = handler.manejarRuntimeException(
                new RuntimeException("Has alcanzado el límite de 2 préstamos activos para tu plan BASICO"));

        assertEquals(422, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("límite"));
    }

    @Test
    void runtimeException_yaTieneLibro_debeRetornar409() {
        var response = handler.manejarRuntimeException(
                new RuntimeException("Ya tienes este libro en préstamo activo"));

        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Ya tienes"));
    }

    @Test
    void runtimeException_noSePudoVerificar_debeRetornar503() {
        var response = handler.manejarRuntimeException(
                new RuntimeException("No se pudo verificar disponibilidad del libro con id: 1"));

        assertEquals(503, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("No se pudo verificar"));
    }

    @Test
    void runtimeException_conMensajeNull_debeRetornar400() {
        var response = handler.manejarRuntimeException(
                new RuntimeException((String) null));

        assertEquals(400, response.getStatusCode().value());
    }
}