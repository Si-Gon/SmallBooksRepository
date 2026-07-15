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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del GlobalExceptionHandler.
 *
 * Se usan dos enfoques complementarios:
 * - MockMvc standalone para probar la validación → 400 (usa un controller inline)
 * - Llamadas directas al handler para probar excepciones de dominio → 404 / 409 / 500
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
    void manejarLibroDuplicado_DebeRetornar409() {
        // Given: excepción de dominio para ISBN duplicado
        LibroDuplicadoException ex = new LibroDuplicadoException("1234567890123");

        // When
        ResponseEntity<Map<String, String>> response = handler.manejarLibroDuplicado(ex);

        // Then
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("Ya existe un libro con ISBN: 1234567890123", response.getBody().get("error"));
    }

    @Test
    void manejarLibroNoEncontrado_DebeRetornar404() {
        // Given: excepción de dominio para libro no encontrado
        LibroNotFoundException ex = new LibroNotFoundException(999L);

        // When
        ResponseEntity<Map<String, String>> response = handler.manejarLibroNoEncontrado(ex);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("Libro no encontrado con id: 999", response.getBody().get("error"));
    }

    // =========================================================
    // MethodArgumentTypeMismatchException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void argumentoInvalido_debeRetornar400() {
        // Given: tipo de argumento incorrecto (ej. string en path variable Long)
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        when(ex.getValue()).thenReturn("texto");

        // When
        ResponseEntity<Map<String, String>> response = handler.manejarArgumentoInvalido(ex);

        // Then
        assertEquals(400, response.getStatusCode().value());
        assertEquals("El valor proporcionado para id no es válido", response.getBody().get("error"));
        assertEquals("ERR-400", response.getBody().get("codigo"));
    }

    // =========================================================
    // HttpMessageNotReadableException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void cuerpoInvalido_debeRetornar400() {
        // Given: JSON mal formado en el cuerpo de la solicitud
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        // When
        ResponseEntity<Map<String, String>> response = handler.manejarCuerpoInvalido(ex);

        // Then
        assertEquals(400, response.getStatusCode().value());
        assertEquals("El cuerpo de la solicitud contiene datos inválidos o está mal formado",
                response.getBody().get("error"));
        assertEquals("ERR-400", response.getBody().get("codigo"));
    }

    // =========================================================
    // MissingServletRequestParameterException → 400 BAD_REQUEST
    // =========================================================

    @Test
    void parametroFaltante_debeRetornar400() {
        // Given: query parameter obligatorio ausente
        MissingServletRequestParameterException ex = mock(MissingServletRequestParameterException.class);
        when(ex.getParameterName()).thenReturn("usuarioId");

        // When
        ResponseEntity<Map<String, String>> response = handler.manejarParametroFaltante(ex);

        // Then
        assertEquals(400, response.getStatusCode().value());
        assertEquals("El parámetro usuarioId es obligatorio", response.getBody().get("error"));
        assertEquals("ERR-400", response.getBody().get("codigo"));
    }

    @Test
    void manejarRuntimeException_DebeRetornar500() {
        // Given: RuntimeException genérica sin tipo específico
        RuntimeException ex = new RuntimeException("Error interno inesperado");

        // When: cae en el fallback genérico
        ResponseEntity<Map<String, String>> response = handler.manejarRuntimeException(ex);

        // Then: el fallback retorna 500 INTERNAL_SERVER_ERROR
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("Error interno del servidor", response.getBody().get("error"));
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
