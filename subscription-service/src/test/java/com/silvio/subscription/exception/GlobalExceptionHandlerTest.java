package com.silvio.subscription.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del GlobalExceptionHandler del Subscription Service.
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
        FieldError fieldError = new FieldError("object", "usuarioId", "El ID del usuario es obligatorio");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, String>> response = handler.manejarValidacion(ex);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("usuarioId"));
        assertEquals("El ID del usuario es obligatorio", response.getBody().get("usuarioId"));
    }

    // =========================================================
    // TokenExtraccionException → 401 UNAUTHORIZED
    // =========================================================

    @Test
    void tokenExtraccion_debeRetornar401() {
        ResponseEntity<Map<String, String>> response = handler.manejarTokenExtraccion(
                new TokenExtraccionException());

        assertEquals(401, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("extraer el usuario"));
    }

    // =========================================================
    // SuscripcionNotFoundException → 404 NOT_FOUND
    // =========================================================

    @Test
    void suscripcionNoEncontrada_debeRetornar404() {
        ResponseEntity<Map<String, String>> response = handler.manejarSuscripcionNotFound(
                new SuscripcionNotFoundException("user123"));

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("user123"));
    }

    // =========================================================
    // MethodArgumentTypeMismatchException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void argumentoInvalido_debeRetornar400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        when(ex.getValue()).thenReturn("texto");

        ResponseEntity<Map<String, String>> response = handler.manejarArgumentoInvalido(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("El valor proporcionado para id no es válido", response.getBody().get("error"));
        assertEquals("ERR-400", response.getBody().get("codigo"));
    }

    // =========================================================
    // HttpMessageNotReadableException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void cuerpoInvalido_debeRetornar400() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        ResponseEntity<Map<String, String>> response = handler.manejarCuerpoInvalido(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("El cuerpo de la solicitud contiene datos inválidos o está mal formado",
                response.getBody().get("error"));
        assertEquals("ERR-400", response.getBody().get("codigo"));
    }

    // =========================================================
    // MissingServletRequestParameterException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void parametroFaltante_debeRetornar400() {
        MissingServletRequestParameterException ex = mock(MissingServletRequestParameterException.class);
        when(ex.getParameterName()).thenReturn("usuarioId");

        ResponseEntity<Map<String, String>> response = handler.manejarParametroFaltante(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("El parámetro usuarioId es obligatorio", response.getBody().get("error"));
        assertEquals("ERR-400", response.getBody().get("codigo"));
    }

    // =========================================================
    // RuntimeException → 500 INTERNAL_SERVER_ERROR (fallback)
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar500() {
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(
                new RuntimeException("Error inesperado en subscription"));

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
