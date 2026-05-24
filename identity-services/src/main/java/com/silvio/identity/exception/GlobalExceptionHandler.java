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

    //  Manejo específico para credenciales incorrectas
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> manejarBadCredentials(BadCredentialsException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Credenciales inválidas");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    //  Manejo para usuario no encontrado
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> manejarUsernameNotFound(UsernameNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    //  Manejo para RuntimeException (errores de negocio)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntimeException(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        
        // Clasificar por mensaje para dar status code apropiado
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ex.getMessage().contains("ya existe")) {
            status = HttpStatus.CONFLICT;  // 409
        } else if (ex.getMessage().contains("expirado") || ex.getMessage().contains("inválido")) {
            status = HttpStatus.UNAUTHORIZED;  // 401
        }
        
        return ResponseEntity.status(status).body(error);
    }
}