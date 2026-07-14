package com.silvio.ingestion.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del GlobalExceptionHandler del Ingestion Service.
 *
 * Verifica que cada excepción de dominio se mapee al HTTP status correcto,
 * incluyendo MaxUploadSizeExceededException → 413.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // =========================================================
    // MaxUploadSizeExceededException → 413 PAYLOAD_TOO_LARGE
    // =========================================================

    @Test
    void maxUploadSizeExceeded_debeRetornar413() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(50 * 1024 * 1024L);

        ResponseEntity<Map<String, String>> response = handler.manejarArchivoGrande(ex);

        assertEquals(413, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("50MB"));
    }

    // =========================================================
    // FormatoNoPermitidoException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void formatoNoPermitido_debeRetornar400() {
        FormatoNoPermitidoException ex = new FormatoNoPermitidoException("docx");

        ResponseEntity<Map<String, String>> response = handler.manejarFormatoNoPermitido(ex);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("docx"));
    }

    // =========================================================
    // ArchivoNoEncontradoException → 404 NOT_FOUND
    // =========================================================

    @Test
    void archivoNoEncontrado_debeRetornar404() {
        ArchivoNoEncontradoException ex = new ArchivoNoEncontradoException(99L);

        ResponseEntity<Map<String, String>> response = handler.manejarArchivoNoEncontrado(ex);

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("99"));
    }

    // =========================================================
    // ErrorLecturaArchivoException → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void errorLecturaArchivo_debeRetornar500() {
        ErrorLecturaArchivoException ex = new ErrorLecturaArchivoException("Error al leer bytes del archivo libro.pdf");

        ResponseEntity<Map<String, String>> response = handler.manejarErrorLectura(ex);

        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Error al leer"));
    }

    // =========================================================
    // ErrorAlmacenamientoException → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void errorAlmacenamiento_debeRetornar500() {
        ErrorAlmacenamientoException ex = new ErrorAlmacenamientoException("Disco lleno");

        ResponseEntity<Map<String, String>> response = handler.manejarErrorAlmacenamiento(ex);

        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").contains("Disco lleno"));
    }

    // =========================================================
    // RuntimeException genérico → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar500() {
        RuntimeException ex = new RuntimeException("Error inesperado");

        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(ex);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error inesperado", response.getBody().get("error"));
    }

    @Test
    void runtimeException_conMensajeNull_debeRetornar500() {
        RuntimeException ex = new RuntimeException((String) null);

        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(ex);

        assertEquals(500, response.getStatusCode().value());
    }
}
