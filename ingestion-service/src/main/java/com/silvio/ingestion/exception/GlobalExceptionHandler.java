package com.silvio.ingestion.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Archivo supera el tamaño máximo
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> manejarArchivoGrande(
            MaxUploadSizeExceededException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "El archivo supera el tamaño máximo permitido (50MB)");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error); // 413
    }

    // Formato de archivo no permitido
    @ExceptionHandler(FormatoNoPermitidoException.class)
    public ResponseEntity<Map<String, String>> manejarFormatoNoPermitido(
            FormatoNoPermitidoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error); // 400
    }

    // Archivo no encontrado
    @ExceptionHandler(ArchivoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> manejarArchivoNoEncontrado(
            ArchivoNoEncontradoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error); // 404
    }

    // Error de lectura de archivo
    @ExceptionHandler(ErrorLecturaArchivoException.class)
    public ResponseEntity<Map<String, String>> manejarErrorLectura(
            ErrorLecturaArchivoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error); // 500
    }

    // Error de almacenamiento (I/O)
    @ExceptionHandler(ErrorAlmacenamientoException.class)
    public ResponseEntity<Map<String, String>> manejarErrorAlmacenamiento(
            ErrorAlmacenamientoException ex) {
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
