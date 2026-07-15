package com.silvio.elending.exception;

import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    // =========================================================
    // FeignException → status HTTP de la respuesta remota
    // =========================================================

    @Test
    void feignException_conStatus404_debeRetornar404() {
        FeignException ex = mock(FeignException.class);
        when(ex.status()).thenReturn(404);
        when(ex.getMessage()).thenReturn("[404] Not Found");

        var response = handler.manejarFeignException(ex);

        assertEquals(404, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
        assertEquals("[404] Not Found", response.getBody().get("detalle"));
    }

    @Test
    void feignException_conStatus409_debeRetornar409() {
        // Conflict en License Service — útil para el patrón de reintento
        FeignException ex = mock(FeignException.class);
        when(ex.status()).thenReturn(409);
        when(ex.getMessage()).thenReturn("[409] Conflict");

        var response = handler.manejarFeignException(ex);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
    }

    @Test
    void feignException_conStatusNegativo_fallback503() {
        // status() == -1 ocurre cuando no hay respuesta HTTP
        // (timeout, conexión rechazada, DNS no resuelto)
        FeignException ex = mock(FeignException.class);
        when(ex.status()).thenReturn(-1);
        when(ex.getMessage()).thenReturn("Connection refused executing GET");

        var response = handler.manejarFeignException(ex);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
        assertEquals("Connection refused executing GET", response.getBody().get("detalle"));
    }

    @Test
    void feignException_conStatusCero_fallback503() {
        FeignException ex = mock(FeignException.class);
        when(ex.status()).thenReturn(0);
        when(ex.getMessage()).thenReturn("Read timed out");

        var response = handler.manejarFeignException(ex);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
    }

    @Test
    void feignException_conMensajeNull_noLanzaNPE() {
        // Caso borde: mensaje null no debe causar NullPointerException
        FeignException ex = mock(FeignException.class);
        when(ex.status()).thenReturn(500);
        when(ex.getMessage()).thenReturn(null);

        var response = handler.manejarFeignException(ex);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
        assertNull(response.getBody().get("detalle"));
    }

    // =========================================================
    // ObjectOptimisticLockingFailureException → 409 CONFLICT
    // =========================================================

    @Test
    void optimisticLock_debeRetornar409() {
        var response = handler.manejarOptimisticLock(
                new ObjectOptimisticLockingFailureException("Prestamo", new RuntimeException("stale state")));

        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("última copia"));
    }

    // =========================================================
    // PrestamoNotFoundException → 404 NOT_FOUND
    // =========================================================

    @Test
    void prestamoNoEncontrado_debeRetornar404() {
        var response = handler.manejarPrestamoNotFound(
                new PrestamoNotFoundException(99L));

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Préstamo no encontrado"));
        assertTrue(response.getBody().get("error").contains("99"));
    }
}
