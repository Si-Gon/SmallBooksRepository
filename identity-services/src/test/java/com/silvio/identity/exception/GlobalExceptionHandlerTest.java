package com.silvio.identity.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del GlobalExceptionHandler del Identity Service.
 *
 * Verifica que cada excepción de dominio y de seguridad
 * se mapeen al HTTP status y mensaje correctos.
 * Identity usa la clave "message" en lugar de "error".
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // =========================================================
    // MethodArgumentNotValidException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void validacion_debeRetornar400ConErrores() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "email", "El email es obligatorio");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, String>> response = handler.manejarValidacion(ex);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("email"));
        assertEquals("El email es obligatorio", response.getBody().get("email"));
    }

    // =========================================================
    // BadCredentialsException → 401 UNAUTHORIZED
    // =========================================================

    @Test
    void badCredentials_debeRetornar401() {
        ResponseEntity<Map<String, String>> response = handler.manejarBadCredentials(
                new BadCredentialsException("bad creds"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Credenciales inválidas", response.getBody().get("message"));
    }

    // =========================================================
    // UsernameNotFoundException → 404 NOT_FOUND
    // =========================================================

    @Test
    void usernameNotFound_debeRetornar404() {
        ResponseEntity<Map<String, String>> response = handler.manejarUsernameNotFound(
                new UsernameNotFoundException("user no encontrado"));

        assertEquals(404, response.getStatusCode().value());
        assertEquals("user no encontrado", response.getBody().get("message"));
    }

    // =========================================================
    // UsuarioNotFoundException → 404 NOT_FOUND
    // =========================================================

    @Test
    void usuarioNoEncontrado_debeRetornar404() {
        ResponseEntity<Map<String, String>> response = handler.manejarUsuarioNoEncontrado(
                new UsuarioNotFoundException("silvio"));

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().get("message").contains("silvio"));
    }

    // =========================================================
    // UsuarioDuplicadoException → 409 CONFLICT
    // =========================================================

    @Test
    void usuarioDuplicado_debeRetornar409() {
        ResponseEntity<Map<String, String>> response = handler.manejarUsuarioDuplicado(
                new UsuarioDuplicadoException("silvio"));

        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().get("message").contains("silvio"));
    }

    // =========================================================
    // TokenExpiradoException / TokenInvalidoException → 401 UNAUTHORIZED
    // =========================================================

    @Test
    void tokenExpirado_debeRetornar401() {
        ResponseEntity<Map<String, String>> response = handler.manejarTokenInvalido(
                new TokenExpiradoException());

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void tokenInvalido_debeRetornar401() {
        ResponseEntity<Map<String, String>> response = handler.manejarTokenInvalido(
                new TokenInvalidoException());

        assertEquals(401, response.getStatusCode().value());
    }

    // =========================================================
    // ContrasenaIncorrectaException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void contrasenaIncorrecta_debeRetornar400() {
        ResponseEntity<Map<String, String>> response = handler.manejarContrasenaIncorrecta(
                new ContrasenaIncorrectaException());

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().get("message").contains("Contraseña"));
    }

    // =========================================================
    // ErrorSeguridadException → 500 INTERNAL_SERVER_ERROR
    // =========================================================

    @Test
    void errorSeguridad_debeRetornar500() {
        ResponseEntity<Map<String, String>> response = handler.manejarErrorSeguridad(
                new ErrorSeguridadException("Error crítico de seguridad"));

        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().get("message").contains("Error crítico"));
    }

    // =========================================================
    // RuntimeException → 500 INTERNAL_SERVER_ERROR (fallback)
    // =========================================================

    @Test
    void runtimeException_generico_debeRetornar500() {
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(
                new RuntimeException("Error inesperado en identity"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error interno del servidor", response.getBody().get("message"));
    }

    @Test
    void runtimeException_conMensajeNull_debeRetornar500() {
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(
                new RuntimeException((String) null));

        assertEquals(500, response.getStatusCode().value());
    }
}
