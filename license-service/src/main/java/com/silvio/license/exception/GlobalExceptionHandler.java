package com.silvio.license.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    // Error de tipo de argumento inválido (path variable, request param)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> manejarArgumentoInvalido(
            MethodArgumentTypeMismatchException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "El valor proporcionado para " + ex.getName() + " no es válido");
        error.put("codigo", "ERR-400");
        log.warn("MethodArgumentTypeMismatchException - parámetro: {}, valor inválido: {}", ex.getName(), ex.getValue(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Cuerpo de solicitud inválido o mal formado
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> manejarCuerpoInvalido(
            HttpMessageNotReadableException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "El cuerpo de la solicitud contiene datos inválidos o está mal formado");
        error.put("codigo", "ERR-400");
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Parámetro de consulta obligatorio faltante
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> manejarParametroFaltante(
            MissingServletRequestParameterException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "El parámetro " + ex.getParameterName() + " es obligatorio");
        error.put("codigo", "ERR-400");
        log.warn("MissingServletRequestParameterException - parámetro: {}", ex.getParameterName(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Violación de restricciones de validación en parámetros (@Positive, @NotBlank, etc.)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> manejarConstraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf(".") + 1) : path;
            errores.put(field, violation.getMessage());
        });
        errores.put("codigo", "ERR-400");
        log.warn("ConstraintViolationException: {}", errores);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
    }

    // NoSuchElementException — reemplazo genérico para exceptions "not found"
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> manejarNoEncontrado(
            NoSuchElementException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage() != null ? ex.getMessage() : "Recurso no encontrado");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error); // 404
    }

    // RuntimeException genérico (fallback)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntimeException(
            RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Error interno del servidor");
        log.error("RuntimeException no manejada: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
