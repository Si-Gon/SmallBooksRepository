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

    // Licencia no encontrada
    @ExceptionHandler(LicenciaNotFoundException.class)
    public ResponseEntity<Map<String, String>> manejarLicenciaNoEncontrada(
            LicenciaNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error); // 404
    }

    // Licencia duplicada
    @ExceptionHandler(LicenciaDuplicadaException.class)
    public ResponseEntity<Map<String, String>> manejarLicenciaDuplicada(
            LicenciaDuplicadaException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error); // 409
    }

    // Conflictos de concurrencia tras agotar reintentos
    @ExceptionHandler(ConflictosConcurrenciaException.class)
    public ResponseEntity<Map<String, String>> manejarConflictosConcurrencia(
            ConflictosConcurrenciaException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error); // 409
    }

    // Sin copias disponibles
    @ExceptionHandler(CopiaNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> manejarCopiaNoDisponible(
            CopiaNoDisponibleException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error); // 422
    }

    // Devolución inválida (todas las copias ya están disponibles)
    @ExceptionHandler(DevolucionInvalidaException.class)
    public ResponseEntity<Map<String, String>> manejarDevolucionInvalida(
            DevolucionInvalidaException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error); // 400
    }

    // Reducción de copias inválida
    @ExceptionHandler(ReduccionCopiasInvalidaException.class)
    public ResponseEntity<Map<String, String>> manejarReduccionCopias(
            ReduccionCopiasInvalidaException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error); // 422
    }

    // Error al devolver copia (OL agotado)
    @ExceptionHandler(ErrorDevolucionException.class)
    public ResponseEntity<Map<String, String>> manejarErrorDevolucion(
            ErrorDevolucionException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error); // 500
    }

    // RuntimeException genérico (fallback)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntimeException(
            RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
