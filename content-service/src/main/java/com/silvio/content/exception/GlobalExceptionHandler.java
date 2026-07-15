package com.silvio.content.exception;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(VerificacionPrestamoException.class)
    public ResponseEntity<Map<String, String>> manejarVerificacionPrestamo(
            VerificacionPrestamoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error); // 503
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<Map<String, String>> manejarAccesoDenegado(
            AccesoDenegadoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error); // 403
    }

    @ExceptionHandler(ArchivoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> manejarArchivoNoEncontrado(
            ArchivoNoEncontradoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error); // 404
    }

    // Error de comunicación con servicio externo (Feign)
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, String>> manejarFeignException(
            FeignException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Error de comunicación con servicio externo");
        error.put("codigo", "ERR-503");
        // ex.status() retorna -1 si no hay respuesta HTTP (timeout, conexión rechazada)
        int status = ex.status();
        if (status <= 0) {
            status = HttpStatus.SERVICE_UNAVAILABLE.value(); // fallback 503
        }
        log.error("FeignException - status: {}, url: {}, mensaje: {}",
                ex.status(),
                ex.request() != null ? ex.request().url() : "N/A",
                ex.getMessage(), ex);
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntimeException(
            RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Error interno del servidor");
        log.error("RuntimeException no manejada: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
