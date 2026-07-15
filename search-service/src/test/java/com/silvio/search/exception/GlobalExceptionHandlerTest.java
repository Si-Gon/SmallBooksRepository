package com.silvio.search.exception;

import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del GlobalExceptionHandler del Search Service.
 *
 * Verifica que cada excepción de dominio y errores de Feign
 * se mapeen al HTTP status correcto.
 *
 * SearchService es un proxy inteligente al CatalogClient. Si el catálogo
 * falla, la FeignException se propaga directamente al handler sin envoltura.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // =========================================================
    // ErrorConsultaCatalogoException → 503 SERVICE_UNAVAILABLE
    // =========================================================

    @Test
    void errorConsultaCatalogo_debeRetornar503() {
        var response = handler.manejarErrorConsulta(
                new ErrorConsultaCatalogoException("buscar", "timeout"));

        assertEquals(503, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Error al buscar"));
        assertTrue(response.getBody().get("error").contains("timeout"));
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
        assertEquals("ERR-503", body.get("codigo"));
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
        when(ex.getMessage()).thenReturn("Connection refused executing GET");

        var response = handler.manejarFeignException(ex);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Error de comunicación con servicio externo", response.getBody().get("error"));
        assertEquals("ERR-503", response.getBody().get("codigo"));
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
        assertNull(response.getBody().get("detalle")); // ya no se expone; ahora se usa 'codigo'
        assertEquals("ERR-503", response.getBody().get("codigo"));
    }

    // =========================================================
    // MethodArgumentTypeMismatchException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void argumentoInvalido_debeRetornar400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        when(ex.getValue()).thenReturn("texto");

        var response = handler.manejarArgumentoInvalido(ex);

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

        var response = handler.manejarCuerpoInvalido(ex);

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

        var response = handler.manejarParametroFaltante(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("El parámetro usuarioId es obligatorio", response.getBody().get("error"));
        assertEquals("ERR-400", response.getBody().get("codigo"));
    }

    // =========================================================
    // RuntimeException → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar500() {
        var response = handler.manejarRuntimeException(
                new RuntimeException("Error inesperado en búsqueda"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error interno del servidor", response.getBody().get("error"));
    }

    @Test
    void runtimeException_conMensajeNull_debeRetornar500() {
        var response = handler.manejarRuntimeException(
                new RuntimeException((String) null));

        assertEquals(500, response.getStatusCode().value());
    }
}
