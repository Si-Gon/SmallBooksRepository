package com.silvio.license.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> manejarValidacion(
            MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errores.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
    }

    // Optimistic locking — dos préstamos simultáneos sobre la misma copia
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> manejarOptimisticLock(
            ObjectOptimisticLockingFailureException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "La última copia fue tomada por otro usuario. Intenta de nuevo.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error); // 409
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntimeException(
            RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());

        HttpStatus status = HttpStatus.NOT_FOUND;
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("ya existe")) {
                status = HttpStatus.CONFLICT;         // 409
            } else if (ex.getMessage().contains("No hay copias")) {
                status = HttpStatus.UNPROCESSABLE_ENTITY; // 422
            } else if (ex.getMessage().contains("muchos usuarios")) {
                status = HttpStatus.CONFLICT;             // 409 — OL agotado tras 3 reintentos
            } else if (ex.getMessage().contains("No se puede reducir")) {
                status = HttpStatus.UNPROCESSABLE_ENTITY; // 422
            }
        }

        return ResponseEntity.status(status).body(error);
    }
}