package com.silvio.search.controller;

import com.silvio.search.dto.SearchResultDTO;
import com.silvio.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del SearchController.
 *
 * Sin Spring Security propio — @WebMvcTest funciona directamente.
 * Verificamos los 3 endpoints GET y sus respuestas de error (503)
 * cuando el SearchService propaga una RuntimeException.
 */
@WebMvcTest(SearchController.class)
@ActiveProfiles("test")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    private SearchResultDTO resultado(Long id, String titulo, Boolean disponible) {
        SearchResultDTO dto = new SearchResultDTO();
        dto.setId(id);
        dto.setTitulo(titulo);
        dto.setAutor("Autor Test");
        dto.setIsbn("978-0-000000-00-0");
        dto.setGenero("Ciencia Ficción");
        dto.setDisponible(disponible);
        return dto;
    }

    // =====================================================================
    // GET /api/search
    // =====================================================================

    @Test
    void obtenerTodos_conLibros_debeRetornar200() throws Exception {
        when(searchService.obtenerTodos()).thenReturn(List.of(
            resultado(1L, "Dune", true),
            resultado(2L, "Fundación", false)
        ));

        mockMvc.perform(get("/api/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].titulo").value("Dune"))
                .andExpect(jsonPath("$[1].titulo").value("Fundación"));

        verify(searchService).obtenerTodos();
    }

    @Test
    void obtenerTodos_listaVacia_debeRetornar200() throws Exception {
        when(searchService.obtenerTodos()).thenReturn(List.of());

        mockMvc.perform(get("/api/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // =====================================================================
    // GET /api/search/disponibles
    // =====================================================================

    @Test
    void disponibles_conLibros_debeRetornar200() throws Exception {
        when(searchService.buscarDisponibles()).thenReturn(List.of(
            resultado(1L, "El Quijote", true)
        ));

        mockMvc.perform(get("/api/search/disponibles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].disponible").value(true));
    }

    @Test
    void disponibles_listaVacia_debeRetornar200ConListaVacia() throws Exception {
        when(searchService.buscarDisponibles()).thenReturn(List.of());

        mockMvc.perform(get("/api/search/disponibles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // =====================================================================
    // GET /api/search/buscar
    // =====================================================================

    @Test
    void buscar_porTitulo_debeRetornar200() throws Exception {
        when(searchService.buscar("Dune", null, null)).thenReturn(List.of(
            resultado(1L, "Dune", true)
        ));

        mockMvc.perform(get("/api/search/buscar").param("titulo", "Dune"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Dune"));

        verify(searchService).buscar("Dune", null, null);
    }

    @Test
    void buscar_sinParametros_debeRetornarTodos() throws Exception {
        when(searchService.buscar(null, null, null)).thenReturn(List.of(
            resultado(1L, "Libro A", true),
            resultado(2L, "Libro B", false)
        ));

        mockMvc.perform(get("/api/search/buscar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // =====================================================================
    // Error interno (RuntimeException) → 500 en todos los endpoints
    // =====================================================================

    @Test
    void obtenerTodos_errorInterno_debeRetornar500() throws Exception {
        when(searchService.obtenerTodos())
            .thenThrow(new RuntimeException("Error inesperado en la base de datos"));

        mockMvc.perform(get("/api/search"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(searchService).obtenerTodos();
    }

    @Test
    void disponibles_errorInterno_debeRetornar500() throws Exception {
        when(searchService.buscarDisponibles())
            .thenThrow(new RuntimeException("Error inesperado en la base de datos"));

        mockMvc.perform(get("/api/search/disponibles"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(searchService).buscarDisponibles();
    }

    @Test
    void buscar_errorInterno_debeRetornar500() throws Exception {
        when(searchService.buscar(any(), any(), any()))
            .thenThrow(new RuntimeException("Error inesperado en la base de datos"));

        mockMvc.perform(get("/api/search/buscar").param("titulo", "java"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(searchService).buscar("java", null, null);
    }
}
