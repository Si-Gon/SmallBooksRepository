package com.silvio.analytics.exception;

import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del GlobalExceptionHandler del Analytics Service.
 *
 * Verifica que cada excepción de dominio y errores de Feign
 * se mapeen al HTTP status correcto.
 *
 * AnalyticsService obtiene datos del E-Lending Service vía Feign.
 * Si elending falla, la FeignException se propaga directamente
 * al handler sin envoltura (try-catch eliminado del Service).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // =========================================================
    // ErrorDatosPrestamosException → 503 SERVICE_UNAVAILABLE
    // =========================================================

    @Test
    void errorDatosPrestamos_debeRetornar503() {
        var response = handler.manejarErrorDatosPrestamos(
                new ErrorDatosPrestamosException("timeout"));

        assertEquals(503, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Error al obtener datos de préstamos"));
    }

    // =========================================================
    // ErrorHistorialUsuarioException → 503 SERVICE_UNAVAILABLE
    // =========================================================

    @Test
    void errorHistorialUsuario_debeRetornar503() {
        var response = handler.manejarErrorHistorial(
                new ErrorHistorialUsuarioException("silvio"));

        assertEquals(503, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Error al obtener historial"));
        assertTrue(response.getBody().get("error").contains("silvio"));
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
    void feignException_conStatusNegativo_fallback503() {
        // status() == -1 ocurre cuando no hay respuesta HTTP
        // (timeout, conexión rechazada, DNS no resuelto)
        FeignException ex = mock(FeignException.class);
        when(ex.status()).thenReturn(-1);
        when(ex.getMessage()).thenReturn("Connection refused executing GET http://elending/api");

        var response = handler.manejarFeignException(ex);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
        assertEquals("Connection refused executing GET http://elending/api", response.getBody().get("detalle"));
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
    // RuntimeException → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar500() {
        var response = handler.manejarRuntimeException(
                new RuntimeException("Error inesperado en analytics"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error inesperado en analytics", response.getBody().get("error"));
    }

    @Test
    void runtimeException_conMensajeNull_debeRetornar500() {
        var response = handler.manejarRuntimeException(
                new RuntimeException((String) null));

        assertEquals(500, response.getStatusCode().value());
    }
}
