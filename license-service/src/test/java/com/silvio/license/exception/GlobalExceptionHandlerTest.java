package com.silvio.license.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del GlobalExceptionHandler del License Service.
 *
 * Verifica que cada excepción de dominio se mapee al HTTP status correcto,
 * incluyendo ObjectOptimisticLockingFailureException → 409.
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
        FieldError fieldError = new FieldError("object", "libroId", "El ID del libro es obligatorio");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, String>> response = handler.manejarValidacion(ex);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("libroId"));
        assertEquals("El ID del libro es obligatorio", response.getBody().get("libroId"));
    }

    // =========================================================
    // ObjectOptimisticLockingFailureException → 409 CONFLICT
    // =========================================================

    @Test
    void optimisticLock_debeRetornar409() {
        ResponseEntity<Map<String, String>> response = handler.manejarOptimisticLock(
                new ObjectOptimisticLockingFailureException("Licencia", new RuntimeException("stale state")));

        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("última copia"));
    }

    // =========================================================
    // LicenciaNotFoundException → 404 NOT_FOUND
    // =========================================================

    @Test
    void licenciaNoEncontrada_debeRetornar404() {
        ResponseEntity<Map<String, String>> response = handler.manejarLicenciaNoEncontrada(
                new LicenciaNotFoundException(1L));

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("No existe licencia"));
        assertTrue(response.getBody().get("error").contains("1"));
    }

    // =========================================================
    // LicenciaDuplicadaException → 409 CONFLICT
    // =========================================================

    @Test
    void licenciaDuplicada_debeRetornar409() {
        ResponseEntity<Map<String, String>> response = handler.manejarLicenciaDuplicada(
                new LicenciaDuplicadaException(1L));

        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Ya existe"));
    }

    // =========================================================
    // ConflictosConcurrenciaException → 409 CONFLICT
    // =========================================================

    @Test
    void conflictosConcurrencia_debeRetornar409() {
        ResponseEntity<Map<String, String>> response = handler.manejarConflictosConcurrencia(
                new ConflictosConcurrenciaException());

        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("muchos usuarios"));
    }

    // =========================================================
    // CopiaNoDisponibleException → 422 UNPROCESSABLE_ENTITY
    // =========================================================

    @Test
    void copiaNoDisponible_debeRetornar422() {
        ResponseEntity<Map<String, String>> response = handler.manejarCopiaNoDisponible(
                new CopiaNoDisponibleException(1L));

        assertEquals(422, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("No hay copias disponibles"));
    }

    // =========================================================
    // DevolucionInvalidaException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void devolucionInvalida_debeRetornar400() {
        ResponseEntity<Map<String, String>> response = handler.manejarDevolucionInvalida(
                new DevolucionInvalidaException());

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody().get("error"));
        assertTrue(response.getBody().get("error").contains("copias"));
    }

    // =========================================================
    // ReduccionCopiasInvalidaException → 422 UNPROCESSABLE_ENTITY
    // =========================================================

    @Test
    void reduccionCopiasInvalida_debeRetornar422() {
        ResponseEntity<Map<String, String>> response = handler.manejarReduccionCopias(
                new ReduccionCopiasInvalidaException(2, 3));

        assertEquals(422, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("reducir"));
    }

    // =========================================================
    // ErrorDevolucionException → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void errorDevolucion_debeRetornar500() {
        ResponseEntity<Map<String, String>> response = handler.manejarErrorDevolucion(
                new ErrorDevolucionException());

        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("devolver"));
    }

    // =========================================================
    // RuntimeException → 500 INTERNAL_SERVER_ERROR (fallback)
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar500() {
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(
                new RuntimeException("Error inesperado en license"));

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
