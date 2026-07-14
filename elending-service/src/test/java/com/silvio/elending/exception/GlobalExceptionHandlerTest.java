package com.silvio.elending.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del GlobalExceptionHandler del E-Lending Service.
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
    // Excepciones de dominio → HTTP status específicos
    // =========================================================

    @Test
    void copiaNoDisponible_debeRetornar422() {
        var response = handler.manejarCopiaNoDisponible(
                new CopiaNoDisponibleException(1L));

        assertEquals(422, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("No hay copias disponibles"));
    }

    @Test
    void limitePrestamosExcedido_debeRetornar422() {
        var response = handler.manejarLimitePrestamos(
                new LimitePrestamosExcedidoException(2, "BASICO"));

        assertEquals(422, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("límite"));
    }

    @Test
    void prestamoDuplicado_debeRetornar409() {
        var response = handler.manejarPrestamoDuplicado(
                new PrestamoDuplicadoException());

        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Ya tienes"));
    }

    @Test
    void verificacionDisponibilidad_debeRetornar503() {
        var response = handler.manejarVerificacionDisponibilidad(
                new VerificacionDisponibilidadException(1L));

        assertEquals(503, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("No se pudo verificar"));
    }

    @Test
    void ultimaCopiaNoDisponible_debeRetornar409() {
        var response = handler.manejarUltimaCopia(
                new UltimaCopiaNoDisponibleException());

        assertEquals(409, response.getStatusCode().value());
    }

    @Test
    void tokenExtraccion_debeRetornar401() {
        var response = handler.manejarTokenExtraccion(
                new TokenExtraccionException());

        assertEquals(401, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("No se pudo extraer"));
    }

    @Test
    void errorRegistroPrestamo_debeRetornar500() {
        var response = handler.manejarErrorRegistro(
                new ErrorRegistroPrestamoException());

        assertEquals(500, response.getStatusCode().value());
    }

    @Test
    void errorCreacionPrestamo_debeRetornar500() {
        var response = handler.manejarErrorCreacion(
                new ErrorCreacionPrestamoException());

        assertEquals(500, response.getStatusCode().value());
    }

    @Test
    void runtimeException_generico_debeRetornar500() {
        var response = handler.manejarRuntimeException(
                new RuntimeException("Error genérico"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error genérico", response.getBody().get("error"));
    }

    @Test
    void runtimeException_conMensajeNull_debeRetornar500() {
        var response = handler.manejarRuntimeException(
                new RuntimeException((String) null));

        assertEquals(500, response.getStatusCode().value());
    }
}
