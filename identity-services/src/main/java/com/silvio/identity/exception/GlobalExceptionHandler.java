package com.silvio.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> manejarValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errores.put(error.getField(), error.getDefaultMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
    }

    // Credenciales incorrectas
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> manejarBadCredentials(BadCredentialsException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "Credenciales inválidas");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // Usuario no encontrado (Spring Security)
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> manejarUsernameNotFound(UsernameNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // Usuario no encontrado (dominio)
    @ExceptionHandler(UsuarioNotFoundException.class)
    public ResponseEntity<Map<String, String>> manejarUsuarioNoEncontrado(UsuarioNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // Usuario duplicado
    @ExceptionHandler(UsuarioDuplicadoException.class)
    public ResponseEntity<Map<String, String>> manejarUsuarioDuplicado(UsuarioDuplicadoException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // Token expirado o inválido
    @ExceptionHandler({TokenExpiradoException.class, TokenInvalidoException.class})
    public ResponseEntity<Map<String, String>> manejarTokenInvalido(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // Contraseña incorrecta
    @ExceptionHandler(ContrasenaIncorrectaException.class)
    public ResponseEntity<Map<String, String>> manejarContrasenaIncorrecta(ContrasenaIncorrectaException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Error interno de seguridad
    @ExceptionHandler(ErrorSeguridadException.class)
    public ResponseEntity<Map<String, String>> manejarErrorSeguridad(ErrorSeguridadException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // RuntimeException genérico (fallback)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntimeException(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
