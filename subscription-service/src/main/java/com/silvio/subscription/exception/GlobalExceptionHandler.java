package com.silvio.subscription.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

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

    @ExceptionHandler(SuscripcionNotFoundException.class)
    public ResponseEntity<Map<String, String>> manejarSuscripcionNotFound(
            SuscripcionNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error); // 404
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

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntimeException(
            RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Error interno del servidor");
        log.error("RuntimeException no manejada: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
