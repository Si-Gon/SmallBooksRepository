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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    // ConstraintViolationException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void constraintViolation_debeRetornar400ConErrores() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn("obtenerPorId.id");
        when(violation.getMessage()).thenReturn("El ID debe ser un número positivo");

        Set<ConstraintViolation<?>> violations = Set.of(violation);
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        ResponseEntity<Map<String, String>> response = handler.manejarConstraintViolation(ex);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("id"));
        assertEquals("El ID debe ser un número positivo", response.getBody().get("id"));
        assertEquals("ERR-400", response.getBody().get("codigo"));
    }

    // =========================================================
    // AccesoDenegadoException → 403 FORBIDDEN
    // =========================================================

    @Test
    void accesoDenegado_debeRetornar403() {
        ResponseEntity<Map<String, String>> response = handler.manejarAccesoDenegado(
                new AccesoDenegadoException("Acceso denegado — no puedes acceder a los datos de otro usuario"));

        assertEquals(403, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Acceso denegado"));
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
