package com.silvio.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.dto.LibroResponseDTO;
import com.silvio.catalog.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del CatalogController usando @WebMvcTest.
 *
 * ¿Qué es @WebMvcTest?
 * Es una anotación de Spring Boot que levanta SOLO la capa web (el controller,
 * filtros, serialización JSON) sin arrancar la base de datos ni otros servicios.
 * El CatalogService se reemplaza con un @MockBean (Mockito), así controlamos
 * exactamente qué devuelve el servicio en cada test.
 *
 * MockMvc simula peticiones HTTP reales sin necesitar un servidor arrancado.
 */
@WebMvcTest(CatalogController.class)
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;  // Simula el cliente HTTP

    @MockBean
    private CatalogService catalogService;  // Mock del servicio — no toca la BD

    @Autowired
    private ObjectMapper objectMapper;  // Convierte objetos Java ↔ JSON

    // =========================================================
    // GET /api/catalog  — listar todos los libros
    // =========================================================

    @Test
    void obtenerTodos_DebeRetornar200ConListaDeLibros() throws Exception {
        // Preparamos qué va a devolver el mock del servicio
        LibroResponseDTO libro = crearLibroResponse(1L, "Cien años de soledad", "García Márquez");
        List<LibroResponseDTO> libros = Arrays.asList(libro);
        when(catalogService.obtenerTodos()).thenReturn(libros);

        // Ejecutamos la petición GET y verificamos la respuesta
        mockMvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                // jsonPath navega el JSON: "$" = raíz, "[0]" = primer elemento
                .andExpect(jsonPath("$[0].titulo").value("Cien años de soledad"))
                .andExpect(jsonPath("$[0].autor").value("García Márquez"));

        verify(catalogService).obtenerTodos();
    }

    @Test
    void obtenerTodos_ConListaVacia_DebeRetornar200() throws Exception {
        when(catalogService.obtenerTodos()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // =========================================================
    // GET /api/catalog/disponibles  — libros disponibles
    // =========================================================

    @Test
    void obtenerDisponibles_DebeRetornar200() throws Exception {
        LibroResponseDTO libro = crearLibroResponse(2L, "El Quijote", "Cervantes");
        when(catalogService.obtenerDisponibles()).thenReturn(Arrays.asList(libro));

        mockMvc.perform(get("/api/catalog/disponibles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("El Quijote"));

        verify(catalogService).obtenerDisponibles();
    }

    // =========================================================
    // GET /api/catalog/{id}  — libro por ID
    // =========================================================

    @Test
    void obtenerPorId_ConIdExistente_DebeRetornar200() throws Exception {
        LibroResponseDTO libro = crearLibroResponse(1L, "1984", "Orwell");
        when(catalogService.obtenerPorId(1L)).thenReturn(libro);

        mockMvc.perform(get("/api/catalog/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.titulo").value("1984"));

        verify(catalogService).obtenerPorId(1L);
    }

    @Test
    void obtenerPorId_ConIdInexistente_DebeRetornar404() throws Exception {
        // El GlobalExceptionHandler convierte RuntimeException → 404
        when(catalogService.obtenerPorId(999L))
                .thenThrow(new RuntimeException("Libro no encontrado con id: 999"));

        mockMvc.perform(get("/api/catalog/999"))
                .andExpect(status().isNotFound());
    }

    // =========================================================
    // GET /api/catalog/buscar  — búsqueda por parámetros
    // =========================================================

    @Test
    void buscar_PorTitulo_DebeRetornar200() throws Exception {
        LibroResponseDTO libro = crearLibroResponse(1L, "Cien años de soledad", "García Márquez");
        when(catalogService.buscar("Cien", null, null)).thenReturn(Arrays.asList(libro));

        mockMvc.perform(get("/api/catalog/buscar").param("titulo", "Cien"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Cien años de soledad"));
    }

    @Test
    void buscar_SinParametros_DebeRetornar200() throws Exception {
        when(catalogService.buscar(null, null, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/catalog/buscar"))
                .andExpect(status().isOk());
    }

    // =========================================================
    // POST /api/catalog  — agregar libro
    // =========================================================

    @Test
    void agregar_ConDatosValidos_DebeRetornar201() throws Exception {
        LibroRequestDTO request = crearLibroRequest();
        LibroResponseDTO response = crearLibroResponse(1L, "Nuevo Libro", "Autor Test");
        when(catalogService.agregar(any(LibroRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        // objectMapper convierte el objeto Java a JSON string
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Nuevo Libro"));

        verify(catalogService).agregar(any(LibroRequestDTO.class));
    }

    @Test
    void agregar_ConISBNDuplicado_DebeRetornar404o409() throws Exception {
        LibroRequestDTO request = crearLibroRequest();
        when(catalogService.agregar(any(LibroRequestDTO.class)))
                .thenThrow(new RuntimeException("Ya existe un libro con ISBN: 1234567890123"));

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // El GlobalExceptionHandler maneja RuntimeException
                .andExpect(status().is4xxClientError());
    }

    @Test
    void agregar_ConTituloVacio_DebeRetornar400() throws Exception {
        // Mandamos un JSON con titulo en blanco — @NotBlank debe rechazarlo
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("");           // inválido
        request.setAutor("Autor");
        request.setIsbn("1234567890123");

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // PUT /api/catalog/{id}  — actualizar libro
    // =========================================================

    @Test
    void actualizar_ConDatosValidos_DebeRetornar200() throws Exception {
        LibroRequestDTO request = crearLibroRequest();
        LibroResponseDTO response = crearLibroResponse(1L, "Libro Actualizado", "Autor Test");
        when(catalogService.actualizar(eq(1L), any(LibroRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/catalog/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Libro Actualizado"));
    }

    @Test
    void actualizar_ConIdInexistente_DebeRetornar404() throws Exception {
        LibroRequestDTO request = crearLibroRequest();
        when(catalogService.actualizar(eq(999L), any(LibroRequestDTO.class)))
                .thenThrow(new RuntimeException("Libro no encontrado con id: 999"));

        mockMvc.perform(put("/api/catalog/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // =========================================================
    // PATCH /api/catalog/{id}/disponibilidad  — cambiar disponibilidad
    // =========================================================

    @Test
    void cambiarDisponibilidad_DebeRetornar200() throws Exception {
        LibroResponseDTO response = crearLibroResponse(1L, "Libro Test", "Autor");
        response.setDisponible(false);
        when(catalogService.cambiarDisponibilidad(1L, false)).thenReturn(response);

        mockMvc.perform(patch("/api/catalog/1/disponibilidad")
                        .param("disponible", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false));
    }

    // =========================================================
    // DELETE /api/catalog/{id}  — eliminar libro
    // =========================================================

    @Test
    void eliminar_ConIdExistente_DebeRetornar204() throws Exception {
        doNothing().when(catalogService).eliminar(1L);

        mockMvc.perform(delete("/api/catalog/1"))
                .andExpect(status().isNoContent());

        verify(catalogService).eliminar(1L);
    }

    @Test
    void eliminar_ConIdInexistente_DebeRetornar404() throws Exception {
        doThrow(new RuntimeException("Libro no encontrado con id: 999"))
                .when(catalogService).eliminar(999L);

        mockMvc.perform(delete("/api/catalog/999"))
                .andExpect(status().isNotFound());
    }

    // =========================================================
    // Métodos helper — evitan repetir código en cada test
    // =========================================================

    /**
     * Crea un LibroResponseDTO con datos mínimos para tests.
     * Recuerda: LibroResponseDTO extiende RepresentationModel (HATEOAS),
     * por eso necesita @EqualsAndHashCode(callSuper=false) en el DTO.
     */
    private LibroResponseDTO crearLibroResponse(Long id, String titulo, String autor) {
        LibroResponseDTO dto = new LibroResponseDTO();
        dto.setId(id);
        dto.setTitulo(titulo);
        dto.setAutor(autor);
        dto.setIsbn("1234567890123");
        dto.setDisponible(true);
        return dto;
    }

    /**
     * Crea un LibroRequestDTO con datos válidos según las validaciones @NotBlank / @Pattern.
     * ISBN-13: exactamente 13 dígitos numéricos.
     */
    private LibroRequestDTO crearLibroRequest() {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro de Prueba");
        request.setAutor("Autor Test");
        request.setIsbn("1234567890123");   // ISBN-13 válido: 13 dígitos
        request.setEditorial("Editorial Test");
        request.setAnioPublicacion(2024);
        request.setIdioma("Español");
        request.setGenero("Ficción");
        return request;
    }
}
