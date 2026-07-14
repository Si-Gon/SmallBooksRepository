package com.silvio.elending.exception;

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

    // Optimistic locking en Prestamo — dos requests concurrentes sobre el mismo préstamo
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> manejarOptimisticLock(
            ObjectOptimisticLockingFailureException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "La última copia del libro fue tomada por otro usuario. Intenta de nuevo.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error); // 409
    }

    // Última copia tomada en License Service (conflicto de concurrencia)
    @ExceptionHandler(UltimaCopiaNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> manejarUltimaCopia(
            UltimaCopiaNoDisponibleException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error); // 409
    }

    // Límite de préstamos activos excedido
    @ExceptionHandler(LimitePrestamosExcedidoException.class)
    public ResponseEntity<Map<String, String>> manejarLimitePrestamos(
            LimitePrestamosExcedidoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error); // 422
    }

    // Préstamo duplicado del mismo libro
    @ExceptionHandler(PrestamoDuplicadoException.class)
    public ResponseEntity<Map<String, String>> manejarPrestamoDuplicado(
            PrestamoDuplicadoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error); // 409
    }

    // Error al verificar disponibilidad con License Service
    @ExceptionHandler(VerificacionDisponibilidadException.class)
    public ResponseEntity<Map<String, String>> manejarVerificacionDisponibilidad(
            VerificacionDisponibilidadException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error); // 503
    }

    // Sin copias disponibles del libro
    @ExceptionHandler(CopiaNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> manejarCopiaNoDisponible(
            CopiaNoDisponibleException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error); // 422
    }

    // Error al registrar préstamo en License Service
    @ExceptionHandler(ErrorRegistroPrestamoException.class)
    public ResponseEntity<Map<String, String>> manejarErrorRegistro(
            ErrorRegistroPrestamoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error); // 500
    }

    // Error al crear préstamo con compensación
    @ExceptionHandler(ErrorCreacionPrestamoException.class)
    public ResponseEntity<Map<String, String>> manejarErrorCreacion(
            ErrorCreacionPrestamoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error); // 500
    }

    // Préstamo no encontrado por ID
    @ExceptionHandler(PrestamoNotFoundException.class)
    public ResponseEntity<Map<String, String>> manejarPrestamoNotFound(
            PrestamoNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error); // 404
    }

    // Token JWT inválido
    @ExceptionHandler(TokenExtraccionException.class)
    public ResponseEntity<Map<String, String>> manejarTokenExtraccion(
            TokenExtraccionException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error); // 401
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
