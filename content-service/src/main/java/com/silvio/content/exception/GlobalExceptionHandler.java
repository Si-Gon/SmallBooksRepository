package com.silvio.content.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntimeException(
            RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("Acceso denegado")) {
                status = HttpStatus.FORBIDDEN;          // 403
            } else if (ex.getMessage().contains("No se pudo verificar")) {
                status = HttpStatus.SERVICE_UNAVAILABLE; // 503
            } else if (ex.getMessage().contains("No se pudo obtener")) {
                status = HttpStatus.NOT_FOUND;           // 404
            }
        }

        return ResponseEntity.status(status).body(error);
    }
}