package com.silvio.ingestion.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    // RuntimeException genérico → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar500() {
        RuntimeException ex = new RuntimeException("Error inesperado");

        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(ex);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error interno del servidor", response.getBody().get("error"));
    }

    @Test
    void runtimeException_conMensajeNull_debeRetornar500() {
        RuntimeException ex = new RuntimeException((String) null);

        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(ex);

        assertEquals(500, response.getStatusCode().value());
    }
}
