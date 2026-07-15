package com.silvio.ingestion.controller;

import com.silvio.ingestion.dto.ArchivoLibroDTO;
import com.silvio.ingestion.exception.ArchivoNoEncontradoException;
import com.silvio.ingestion.exception.ErrorAlmacenamientoException;
import com.silvio.ingestion.exception.FormatoNoPermitidoException;
import com.silvio.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del IngestionController.
 *
 * Puntos de interés:
 * - POST /upload/{libroId} usa multipart/form-data (no JSON) — se simula
 *   con MockMvc.multipart() y MockMultipartFile
 * - GET /{libroId} devuelve ArchivoLibroDTO
 * - GET /{libroId}/bytes devuelve byte[] con content-type octet-stream
 * - DELETE /{libroId} devuelve 204 (sin body)
 */
@WebMvcTest(IngestionController.class)
@ActiveProfiles("test")
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionService ingestionService;

    // =====================================================================
    // Validación @Positive en @PathVariable Long
    // =====================================================================

    @Test
    void subirArchivo_conIdNegativo_debeRetornar400() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "test.pdf", "application/pdf", "test".getBytes());

        mockMvc.perform(multipart("/api/ingestion/upload/-1").file(archivo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.libroId").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(ingestionService, never()).subirArchivo(any(), any());
    }

    @Test
    void obtenerInfo_conIdNegativo_debeRetornar400() throws Exception {
        mockMvc.perform(get("/api/ingestion/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.libroId").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(ingestionService, never()).obtenerInfo(anyLong());
    }

    @Test
    void obtenerBytes_conIdNegativo_debeRetornar400() throws Exception {
        mockMvc.perform(get("/api/ingestion/-1/bytes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.libroId").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(ingestionService, never()).obtenerBytes(anyLong());
    }

    @Test
    void eliminar_conIdNegativo_debeRetornar400() throws Exception {
        mockMvc.perform(delete("/api/ingestion/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.libroId").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(ingestionService, never()).eliminar(anyLong());
    }

    private ArchivoLibroDTO dto(Long libroId) {
        ArchivoLibroDTO dto = new ArchivoLibroDTO();
        dto.setId(1L);
        dto.setLibroId(libroId);
        dto.setNombreArchivo("libro.pdf");
        dto.setFormato("PDF");
        dto.setTamanio(2048L);
        dto.setFechaSubida(LocalDateTime.now());
        return dto;
    }

    // =====================================================================
    // POST /api/ingestion/upload/{libroId}
    // =====================================================================

    @Test
    void subirArchivo_pdf_debeRetornar201ConDTO() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "libro.pdf", "application/pdf", "contenido".getBytes());

        when(ingestionService.subirArchivo(eq(1L), any())).thenReturn(dto(1L));

        mockMvc.perform(multipart("/api/ingestion/upload/1").file(archivo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libroId").value(1))
                .andExpect(jsonPath("$.formato").value("PDF"))
                .andExpect(jsonPath("$.nombreArchivo").value("libro.pdf"));

        verify(ingestionService).subirArchivo(eq(1L), any());
    }

    @Test
    void subirArchivo_formatoInvalido_debeRetornar400() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "doc.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "contenido".getBytes());

        // FormatoNoPermitidoException → GlobalExceptionHandler devuelve 400
        when(ingestionService.subirArchivo(eq(1L), any()))
            .thenThrow(new FormatoNoPermitidoException("docx"));

        mockMvc.perform(multipart("/api/ingestion/upload/1").file(archivo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // =====================================================================
    // GET /api/ingestion/{libroId}
    // =====================================================================

    @Test
    void obtenerInfo_archivoExiste_debeRetornar200() throws Exception {
        when(ingestionService.obtenerInfo(1L)).thenReturn(dto(1L));

        mockMvc.perform(get("/api/ingestion/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libroId").value(1))
                .andExpect(jsonPath("$.formato").value("PDF"))
                .andExpect(jsonPath("$.tamanio").value(2048));
    }

    @Test
    void obtenerInfo_archivoNoExiste_debeRetornar404() throws Exception {
        // ArchivoNoEncontradoException → GlobalExceptionHandler devuelve 404
        when(ingestionService.obtenerInfo(99L))
            .thenThrow(new ArchivoNoEncontradoException(99L));

        mockMvc.perform(get("/api/ingestion/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    // =====================================================================
    // GET /api/ingestion/{libroId}/bytes
    // =====================================================================

    @Test
    void obtenerBytes_archivoExiste_debeRetornar200ConBytes() throws Exception {
        byte[] bytes = "datos del pdf".getBytes();
        when(ingestionService.obtenerBytes(1L)).thenReturn(bytes);

        mockMvc.perform(get("/api/ingestion/1/bytes"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void obtenerBytes_archivoNoExiste_debeRetornar404() throws Exception {
        when(ingestionService.obtenerBytes(5L))
            .thenThrow(new ArchivoNoEncontradoException(5L));

        mockMvc.perform(get("/api/ingestion/5/bytes"))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // DELETE /api/ingestion/{libroId}
    // =====================================================================

    @Test
    void eliminar_archivoExiste_debeRetornar204() throws Exception {
        doNothing().when(ingestionService).eliminar(1L);

        mockMvc.perform(delete("/api/ingestion/1"))
                .andExpect(status().isNoContent());

        verify(ingestionService).eliminar(1L);
    }

    @Test
    void eliminar_archivoNoExiste_debeRetornar404() throws Exception {
        doThrow(new ArchivoNoEncontradoException(7L))
            .when(ingestionService).eliminar(7L);

        mockMvc.perform(delete("/api/ingestion/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    // =====================================================================
    // Errores de almacenamiento e internos
    // =====================================================================

    @Test
    void subirArchivo_errorAlmacenamiento_debeRetornar500() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "libro.pdf", "application/pdf", "contenido".getBytes());

        // ErrorAlmacenamientoException → GlobalExceptionHandler devuelve 500
        when(ingestionService.subirArchivo(eq(1L), any()))
            .thenThrow(new ErrorAlmacenamientoException("Error de escritura: Disco lleno"));

        mockMvc.perform(multipart("/api/ingestion/upload/1").file(archivo))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(ingestionService).subirArchivo(eq(1L), any());
    }

    @Test
    void obtenerInfo_errorInterno_debeRetornar500() throws Exception {
        // RuntimeException → GlobalExceptionHandler devuelve 500
        when(ingestionService.obtenerInfo(1L))
            .thenThrow(new RuntimeException("Error inesperado de base de datos"));

        mockMvc.perform(get("/api/ingestion/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void subirArchivo_libroNoEncontrado_debeRetornar404() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "libro.pdf", "application/pdf", "contenido".getBytes());

        // ArchivoNoEncontradoException → GlobalExceptionHandler devuelve 404
        // (cuando el libroId no existe en el catálogo)
        when(ingestionService.subirArchivo(eq(99L), any()))
            .thenThrow(new ArchivoNoEncontradoException(99L));

        mockMvc.perform(multipart("/api/ingestion/upload/99").file(archivo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        verify(ingestionService).subirArchivo(eq(99L), any());
    }
}
