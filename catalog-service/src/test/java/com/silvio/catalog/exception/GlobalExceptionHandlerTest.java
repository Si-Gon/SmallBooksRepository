package com.silvio.catalog.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del GlobalExceptionHandler.
 *
 * Se usan dos enfoques complementarios:
 * - MockMvc standalone para probar la validación → 400 (usa un controller inline)
 * - Llamadas directas al handler para probar RuntimeException → 404 / 409
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void manejarValidacion_DebeRetornar400() throws Exception {
        // Given: creamos un MockMvc standalone con un controller mínimo + handler
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TestValidationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // When: enviamos JSON vacío — @NotBlank en "nombre" debe fallar
        // Then: retorna 400 BAD_REQUEST con mapa de errores
        mockMvc.perform(post("/test/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.nombre").exists());
    }

    @Test
    void manejarRuntimeException_ConMensajeYaExiste_DebeRetornar409() {
        // Given
        // El servicio lanza "Ya existe..." con mayúscula inicial, y el handler
        // ahora usa contains() con toLowerCase() para ser case-insensitive.
        RuntimeException ex = new RuntimeException("Ya existe un libro con ISBN: 1234567890123");

        // When
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(ex);

        // Then
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("Ya existe un libro con ISBN: 1234567890123", response.getBody().get("error"));
    }

    @Test
    void manejarRuntimeException_ConMensajeNotFound_DebeRetornar404() {
        // Given
        RuntimeException ex = new RuntimeException("Libro no encontrado con id: 999");

        // When
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(ex);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("Libro no encontrado con id: 999", response.getBody().get("error"));
    }

    @Test
    void manejarRuntimeException_ConMensajeNull_DebeRetornar404() {
        // Given
        RuntimeException ex = new RuntimeException();

        // When
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(ex);

        // Then: mensaje null → no contiene "ya existe" → 404 NOT_FOUND
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void manejarRuntimeException_ConMensajeGenerico_DebeRetornar404() {
        // Given: mensaje que no contiene ni "ya existe" ni patrones especiales
        RuntimeException ex = new RuntimeException("Error interno inesperado");

        // When
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(ex);

        // Then: mensaje genérico → 404 NOT_FOUND (default)
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Error interno inesperado", response.getBody().get("error"));
    }

    @Test
    void manejarValidacion_DebeRetornar400ConMultiplesErrores() throws Exception {
        // Given: un DTO con múltiples campos inválidos
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TestValidationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // When: enviamos JSON con nombre vacío y email inválido
        mockMvc.perform(post("/test/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\", \"email\": \"invalido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.nombre").exists())
                .andExpect(jsonPath("$.email").exists());
    }

    // ---------------------------------------------------------------
    // Controller inline mínimo para disparar MethodArgumentNotValidException
    // ---------------------------------------------------------------

    @RestController
    static class TestValidationController {
        @PostMapping("/test/validar")
        public ResponseEntity<?> testValidar(@Valid @RequestBody TestRequest request) {
            return ResponseEntity.ok().build();
        }
    }

    @Data
    static class TestRequest {
        @NotBlank(message = "El nombre es obligatorio")
        private String nombre;

        @jakarta.validation.constraints.Email(message = "Email inválido")
        private String email;
    }
}
