package com.silvio.content.controller;

import com.silvio.content.exception.AccesoDenegadoException;
import com.silvio.content.exception.ArchivoNoEncontradoException;
import com.silvio.content.exception.VerificacionPrestamoException;
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
        // AccesoDenegadoException → GlobalExceptionHandler devuelve 403
        when(contentService.obtenerArchivo(1L, "Bearer token"))
            .thenThrow(new AccesoDenegadoException(1L));

        mockMvc.perform(get("/api/content/1")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void descargarArchivo_errorEnLending_debeRetornar503() throws Exception {
        // VerificacionPrestamoException → GlobalExceptionHandler devuelve 503
        when(contentService.obtenerArchivo(2L, "Bearer token"))
            .thenThrow(new VerificacionPrestamoException("Connection refused"));

        mockMvc.perform(get("/api/content/2")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void descargarArchivo_archivoNoEncontrado_debeRetornar404() throws Exception {
        // ArchivoNoEncontradoException → GlobalExceptionHandler devuelve 404
        when(contentService.obtenerArchivo(3L, "Bearer token"))
                .thenThrow(new ArchivoNoEncontradoException(3L));

        mockMvc.perform(get("/api/content/3")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void descargarArchivo_errorInterno_debeRetornar500() throws Exception {
        // RuntimeException → GlobalExceptionHandler devuelve 500
        when(contentService.obtenerArchivo(4L, "Bearer token"))
                .thenThrow(new RuntimeException("Error inesperado de E/S al leer el archivo"));

        mockMvc.perform(get("/api/content/4")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(contentService).obtenerArchivo(4L, "Bearer token");
    }
}
