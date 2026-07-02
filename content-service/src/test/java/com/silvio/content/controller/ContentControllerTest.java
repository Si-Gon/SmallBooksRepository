package com.silvio.content.controller;

import com.silvio.content.service.ContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del ContentController.
 *
 * ContentController es simple: recibe GET /{libroId} con header Authorization
 * y delega todo al ContentService. El controller NO tiene Spring Security propio,
 * así que @WebMvcTest funciona directamente sin configuración adicional.
 *
 * Verificamos que:
 * - El archivo llega correctamente al body de la respuesta (bytes)
 * - Los códigos de error del GlobalExceptionHandler se mapean bien
 */
@WebMvcTest(ContentController.class)
@ActiveProfiles("test")
class ContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContentService contentService;

    // =====================================================================
    // GET /api/content/{libroId}
    // =====================================================================

    @Test
    void descargarArchivo_conPrestamoActivo_debeRetornar200ConBytes() throws Exception {
        byte[] bytes = "PDF de prueba".getBytes();
        when(contentService.obtenerArchivo(1L, "Bearer token_valido"))
            .thenReturn(bytes);

        mockMvc.perform(get("/api/content/1")
                        .header("Authorization", "Bearer token_valido"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"libro_1\""))
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(content().bytes(bytes));

        verify(contentService).obtenerArchivo(1L, "Bearer token_valido");
    }

    @Test
    void descargarArchivo_sinPrestamo_debeRetornar403() throws Exception {
        // "Acceso denegado" en el mensaje → GlobalExceptionHandler devuelve 403
        when(contentService.obtenerArchivo(1L, "Bearer token"))
            .thenThrow(new RuntimeException("Acceso denegado — no tienes un préstamo activo del libro con id: 1"));

        mockMvc.perform(get("/api/content/1")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void descargarArchivo_errorEnLending_debeRetornar503() throws Exception {
        // "No se pudo verificar" → GlobalExceptionHandler devuelve 503
        when(contentService.obtenerArchivo(2L, "Bearer token"))
            .thenThrow(new RuntimeException("No se pudo verificar el préstamo: Connection refused"));

        mockMvc.perform(get("/api/content/2")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void descargarArchivo_archivoNoEncontrado_debeRetornar404() throws Exception {
        // "No se pudo obtener" → GlobalExceptionHandler devuelve 404
        when(contentService.obtenerArchivo(3L, "Bearer token"))
            .thenThrow(new RuntimeException("No se pudo obtener el archivo del libro con id: 3"));

        mockMvc.perform(get("/api/content/3")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
