package com.silvio.elending.exception;

import feign.FeignException;
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

    // Sin copias disponibles del libro
    @ExceptionHandler(CopiaNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> manejarCopiaNoDisponible(
            CopiaNoDisponibleException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error); // 422
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
