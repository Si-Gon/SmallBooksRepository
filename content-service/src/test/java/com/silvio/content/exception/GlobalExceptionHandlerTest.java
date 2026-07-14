package com.silvio.content.exception;

import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del GlobalExceptionHandler del Content Service.
 *
 * Verifica que cada excepción de dominio y errores de Feign
 * se mapeen al HTTP status correcto.
 *
 * El handler fue refactorizado para delegar el manejo de FeignException
 * directamente (sin try-catch en el Service). Los casos clave son:
 *   - status > 0  → se usa el status HTTP de la respuesta remota
 *   - status <= 0 → fallback 503 (timeout, conexión rechazada)
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // =========================================================
    // VerificacionPrestamoException → 503 SERVICE_UNAVAILABLE
    // =========================================================

    @Test
    void verificacionPrestamo_debeRetornar503() {
        var response = handler.manejarVerificacionPrestamo(
                new VerificacionPrestamoException("elending-service no disponible"));

        assertEquals(503, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("No se pudo verificar"));
    }

    // =========================================================
    // AccesoDenegadoException → 403 FORBIDDEN
    // =========================================================

    @Test
    void accesoDenegado_debeRetornar403() {
        var response = handler.manejarAccesoDenegado(
                new AccesoDenegadoException(1L));

        assertEquals(403, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Acceso denegado"));
    }

    // =========================================================
    // ArchivoNoEncontradoException → 404 NOT_FOUND
    // =========================================================

    @Test
    void archivoNoEncontrado_debeRetornar404() {
        var response = handler.manejarArchivoNoEncontrado(
                new ArchivoNoEncontradoException(42L));

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("No se pudo obtener el archivo"));
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
        Map<String, String> body = response.getBody();
        assertEquals("Error de comunicación con servicio externo", body.get("error"));
        assertEquals("[404] Not Found", body.get("detalle"));
    }

    @Test
    void feignException_conStatus503_debeRetornar503() {
        FeignException ex = mock(FeignException.class);
        when(ex.status()).thenReturn(503);
        when(ex.getMessage()).thenReturn("[503] Service Unavailable");

        var response = handler.manejarFeignException(ex);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
    }

    @Test
    void feignException_conStatus409_debeRetornar409() {
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
        when(ex.getMessage()).thenReturn("Connection refused");

        var response = handler.manejarFeignException(ex);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
        assertEquals("Connection refused", response.getBody().get("detalle"));
    }

    @Test
    void feignException_conStatusCero_fallback503() {
        // status() == 0 es otro caso sin respuesta HTTP válida
        FeignException ex = mock(FeignException.class);
        when(ex.status()).thenReturn(0);
        when(ex.getMessage()).thenReturn("Read timed out");

        var response = handler.manejarFeignException(ex);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
    }

    // =========================================================
    // RuntimeException → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar500() {
        var response = handler.manejarRuntimeException(
                new RuntimeException("Error inesperado"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error inesperado", response.getBody().get("error"));
    }

    @Test
    void runtimeException_conMensajeNull_debeRetornar500() {
        var response = handler.manejarRuntimeException(
                new RuntimeException((String) null));

        assertEquals(500, response.getStatusCode().value());
    }
}
