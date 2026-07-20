package com.silvio.content.controller;

import com.silvio.content.exception.AccesoDenegadoException;
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
 * ContentController es simple: recibe GET /{libroId} con header X-User-Id
 * propagado por el Gateway y delega todo al ContentService. El controller NO tiene Spring Security propio,
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

    // ─── @Positive validation ─────────────────────────────────────────

    @Test
    void descargarArchivo_conIdNegativo_debeRetornar400() throws Exception {
        // @Validated + @Positive — libroId negativo debe disparar ConstraintViolationException
        mockMvc.perform(get("/api/content/-1")
                        .header("X-User-Id", "silvio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.libroId").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(contentService, never()).obtenerArchivo(anyLong(), anyString());
    }

    @Test
    void descargarArchivo_conIdNoNumerico_debeRetornar400() throws Exception {
        // "abc" no es Long — dispara MethodArgumentTypeMismatchException
        mockMvc.perform(get("/api/content/abc")
                        .header("X-User-Id", "silvio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(contentService, never()).obtenerArchivo(anyLong(), anyString());
    }

    // ─── Tests existentes ─────────────────────────────────────────────

    @Test
    void descargarArchivo_conPrestamoActivo_debeRetornar200ConBytes() throws Exception {
        byte[] bytes = "PDF de prueba".getBytes();
        when(contentService.obtenerArchivo(1L, "silvio"))
            .thenReturn(bytes);

        mockMvc.perform(get("/api/content/1")
                        .header("X-User-Id", "silvio"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"libro_1\""))
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(content().bytes(bytes));

        verify(contentService).obtenerArchivo(1L, "silvio");
    }

    @Test
    void descargarArchivo_sinPrestamo_debeRetornar403() throws Exception {
        // AccesoDenegadoException → GlobalExceptionHandler devuelve 403
        when(contentService.obtenerArchivo(1L, "silvio"))
            .thenThrow(new AccesoDenegadoException(1L));

        mockMvc.perform(get("/api/content/1")
                        .header("X-User-Id", "silvio"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void descargarArchivo_errorInterno_debeRetornar500() throws Exception {
        // RuntimeException → GlobalExceptionHandler devuelve 500
        when(contentService.obtenerArchivo(4L, "silvio"))
                .thenThrow(new RuntimeException("Error inesperado de E/S al leer el archivo"));

        mockMvc.perform(get("/api/content/4")
                        .header("X-User-Id", "silvio"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(contentService).obtenerArchivo(4L, "silvio");
    }
}
