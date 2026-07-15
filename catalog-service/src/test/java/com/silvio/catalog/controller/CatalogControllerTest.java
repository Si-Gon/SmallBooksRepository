package com.silvio.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.dto.LibroResponseDTO;
import com.silvio.catalog.exception.LibroNotFoundException;
import com.silvio.catalog.exception.LibroDuplicadoException;
import com.silvio.catalog.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
@ActiveProfiles("test")
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
        Page<LibroResponseDTO> page = new PageImpl<>(Arrays.asList(libro));
        when(catalogService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // Ejecutamos la petición GET y verificamos la respuesta paginada
        mockMvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                // jsonPath navega el JSON de la página: "$.content" = array interno
                .andExpect(jsonPath("$.content[0].titulo").value("Cien años de soledad"))
                .andExpect(jsonPath("$.content[0].autor").value("García Márquez"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(catalogService).obtenerTodos(any(Pageable.class));
    }

    @Test
    void obtenerTodos_ConListaVacia_DebeRetornar200() throws Exception {
        Page<LibroResponseDTO> page = new PageImpl<>(Collections.emptyList());
        when(catalogService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void obtenerTodos_ConTamanioPersonalizado_DebeRetornarPaginaConSizeCorrecto() throws Exception {
        // Given: 2 libros en página de tamaño 2 de 5 totales
        LibroResponseDTO libro1 = crearLibroResponse(1L, "Cien años de soledad", "García Márquez");
        LibroResponseDTO libro2 = crearLibroResponse(2L, "El Quijote", "Cervantes");
        Page<LibroResponseDTO> page = new PageImpl<>(
                Arrays.asList(libro1, libro2),
                PageRequest.of(0, 2),
                5L
        );
        when(catalogService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // When & Then: usar ?size=2
        mockMvc.perform(get("/api/catalog").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        verify(catalogService).obtenerTodos(any(Pageable.class));
    }

    @Test
    void obtenerTodos_ConPaginaYSortPersonalizados_DebeRetornarPaginaCorrecta() throws Exception {
        // Given: página 1 (índice 1) con size=1, sort=autor,desc
        LibroResponseDTO libro = crearLibroResponse(3L, "Rayuela", "Cortázar");
        Page<LibroResponseDTO> page = new PageImpl<>(
                Arrays.asList(libro),
                PageRequest.of(1, 1),
                3L
        );
        when(catalogService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // When & Then: usar ?page=1&size=1&sort=autor,desc
        mockMvc.perform(get("/api/catalog")
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "autor,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Rayuela"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));

        verify(catalogService).obtenerTodos(any(Pageable.class));
    }

    @Test
    void obtenerTodos_ConPageInvalido_DebeUsarDefaultYRetornar200() throws Exception {
        // Given: Spring Data Web Support trata page negativa como 0
        LibroResponseDTO libro = crearLibroResponse(1L, "Test", "Autor");
        Page<LibroResponseDTO> page = new PageImpl<>(Arrays.asList(libro));
        when(catalogService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // When: page=-1 debería ser manejado por Spring (default a 0)
        mockMvc.perform(get("/api/catalog").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Test"));

        verify(catalogService).obtenerTodos(any(Pageable.class));
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
    // GET /api/catalog/{id}  — @Positive validation
    // =========================================================

    @Test
    void obtenerPorId_conIdNegativo_debeRetornar400() throws Exception {
        // @Validated + @Positive — id negativo debe disparar ConstraintViolationException
        mockMvc.perform(get("/api/catalog/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.id").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(catalogService, never()).obtenerPorId(anyLong());
    }

    @Test
    void obtenerPorId_conIdCero_debeRetornar400() throws Exception {
        // Cero no es positivo — debe disparar ConstraintViolationException
        mockMvc.perform(get("/api/catalog/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.id").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(catalogService, never()).obtenerPorId(anyLong());
    }

    @Test
    void obtenerPorId_conIdNoNumerico_debeRetornar400() throws Exception {
        // "abc" no es Long — dispara MethodArgumentTypeMismatchException
        mockMvc.perform(get("/api/catalog/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El valor proporcionado para id no es válido"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(catalogService, never()).obtenerPorId(anyLong());
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
        when(catalogService.obtenerPorId(999L))
                .thenThrow(new LibroNotFoundException(999L));

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
    void agregar_ConISBNDuplicado_DebeRetornar409() throws Exception {
        LibroRequestDTO request = crearLibroRequest();
        when(catalogService.agregar(any(LibroRequestDTO.class)))
                .thenThrow(new LibroDuplicadoException("1234567890123"));

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
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
    // PUT /api/catalog/{id}  — @Positive validation
    // =========================================================

    @Test
    void actualizar_conIdNegativo_debeRetornar400() throws Exception {
        LibroRequestDTO request = crearLibroRequest();
        mockMvc.perform(put("/api/catalog/-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.id").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(catalogService, never()).actualizar(anyLong(), any());
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
                .thenThrow(new LibroNotFoundException(999L));

        mockMvc.perform(put("/api/catalog/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // =========================================================
    // PATCH /api/catalog/{id}/disponibilidad  — @Positive validation
    // =========================================================

    @Test
    void cambiarDisponibilidad_conIdNegativo_debeRetornar400() throws Exception {
        mockMvc.perform(patch("/api/catalog/-1/disponibilidad")
                        .param("disponible", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.id").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(catalogService, never()).cambiarDisponibilidad(anyLong(), anyBoolean());
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
    // DELETE /api/catalog/{id}  — @Positive validation
    // =========================================================

    @Test
    void eliminar_conIdNegativo_debeRetornar400() throws Exception {
        mockMvc.perform(delete("/api/catalog/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.id").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(catalogService, never()).eliminar(anyLong());
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
        doThrow(new LibroNotFoundException(999L))
                .when(catalogService).eliminar(999L);

        mockMvc.perform(delete("/api/catalog/999"))
                .andExpect(status().isNotFound());
    }

    // =========================================================
    // GET /api/catalog/buscar — búsqueda por autor
    // =========================================================

    @Test
    void buscar_PorAutor_DebeRetornar200() throws Exception {
        LibroResponseDTO libro = crearLibroResponse(1L, "Cien años de soledad", "García Márquez");
        when(catalogService.buscar(null, "García", null)).thenReturn(Arrays.asList(libro));

        mockMvc.perform(get("/api/catalog/buscar").param("autor", "García"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Cien años de soledad"));
    }

    // =========================================================
    // GET /api/catalog/buscar — búsqueda por género
    // =========================================================

    @Test
    void buscar_PorGenero_DebeRetornar200() throws Exception {
        LibroResponseDTO libro = crearLibroResponse(1L, "Cien años de soledad", "García Márquez");
        when(catalogService.buscar(null, null, "Ficción")).thenReturn(Arrays.asList(libro));

        mockMvc.perform(get("/api/catalog/buscar").param("genero", "Ficción"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Cien años de soledad"));
    }

    // =========================================================
    // GET /api/catalog/buscar — búsqueda combinada
    // =========================================================

    @Test
    void buscar_ConParametrosCombinados_DebeRetornar200() throws Exception {
        LibroResponseDTO libro = crearLibroResponse(1L, "Cien años de soledad", "García Márquez");
        when(catalogService.buscar("Cien", "García", "Novela")).thenReturn(Arrays.asList(libro));

        mockMvc.perform(get("/api/catalog/buscar")
                        .param("titulo", "Cien")
                        .param("autor", "García")
                        .param("genero", "Novela"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Cien años de soledad"));
    }

    // =========================================================
    // PATCH /api/catalog/{id}/disponibilidad — ID inexistente
    // =========================================================

    @Test
    void cambiarDisponibilidad_ConIdInexistente_DebeRetornar404() throws Exception {
        when(catalogService.cambiarDisponibilidad(999L, false))
                .thenThrow(new LibroNotFoundException(999L));

        mockMvc.perform(patch("/api/catalog/999/disponibilidad")
                        .param("disponible", "false"))
                .andExpect(status().isNotFound());
    }

    // =========================================================
    // PUT /api/catalog/{id} — DTO inválido (sin título)
    // =========================================================

    @Test
    void actualizar_ConDatosInvalidos_DebeRetornar400() throws Exception {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setAutor("Autor Test");
        request.setIsbn("1234567890123");
        // titulo es null — @NotBlank debe rechazar

        mockMvc.perform(put("/api/catalog/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // POST /api/catalog — validación: autor vacío
    // =========================================================

    @Test
    void agregar_ConAutorVacio_DebeRetornar400() throws Exception {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro Test");
        request.setAutor("");           // inválido — @NotBlank
        request.setIsbn("1234567890123");

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // POST /api/catalog — validación: ISBN con formato incorrecto
    // =========================================================

    @Test
    void agregar_ConIsbnInvalido_DebeRetornar400() throws Exception {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro Test");
        request.setAutor("Autor Test");
        request.setIsbn("ISBN-invalido");   // no cumple @Pattern

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // GET /api/catalog/disponibles — lista vacía
    // =========================================================

    @Test
    void obtenerDisponibles_ConListaVacia_DebeRetornar200() throws Exception {
        when(catalogService.obtenerDisponibles()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/catalog/disponibles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // =========================================================
    // GET /api/catalog/buscar — parámetros vacíos (blank)
    // =========================================================

    @Test
    void buscar_ConParametrosVacios_DebeRetornar200() throws Exception {
        when(catalogService.buscar("", "", "")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/catalog/buscar")
                        .param("titulo", "")
                        .param("autor", "")
                        .param("genero", ""))
                .andExpect(status().isOk());
    }

    // =========================================================
    // POST /api/catalog — validación: portadaUrl inválida
    // =========================================================

    @Test
    void agregar_ConPortadaUrlInvalida_DebeRetornar400() throws Exception {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro Test");
        request.setAutor("Autor Test");
        request.setIsbn("1234567890123");
        request.setPortadaUrl("ftp://mal.com/img.jpg");  // no comienza con http:// o https://

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // POST /api/catalog — validación: año de publicación fuera de rango
    // =========================================================

    @Test
    void agregar_ConAnioPublicacionInvalido_DebeRetornar400() throws Exception {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro Test");
        request.setAutor("Autor Test");
        request.setIsbn("1234567890123");
        request.setAnioPublicacion(1400);  // < 1450 — inválido

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agregar_ConAnioPublicacionFuturo_DebeRetornar400() throws Exception {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro Test");
        request.setAutor("Autor Test");
        request.setIsbn("1234567890123");
        request.setAnioPublicacion(2101);  // > 2100 — inválido

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // POST /api/catalog — validación: ISBN nulo
    // =========================================================

    @Test
    void agregar_ConIsbnNulo_DebeRetornar400() throws Exception {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro Test");
        request.setAutor("Autor Test");
        // isbn = null — @NotBlank debe rechazar

        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // PATCH /api/catalog/{id}/disponibilidad — sin parámetro disponible
    // =========================================================

    @Test
    void cambiarDisponibilidad_SinParametroDisponible_DebeRetornar400() throws Exception {
        // disponible es @RequestParam requerido (required = true por defecto)
        mockMvc.perform(patch("/api/catalog/1/disponibilidad"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // HATEOAS — verificación de enlaces _links en respuestas
    // =========================================================

    @Test
    void obtenerTodos_DebeIncluirEnlacesHATEOAS() throws Exception {
        // Given
        LibroResponseDTO libro1 = crearLibroResponse(1L, "Cien años de soledad", "García Márquez");
        LibroResponseDTO libro2 = crearLibroResponse(2L, "El Quijote", "Cervantes");
        Page<LibroResponseDTO> page = new PageImpl<>(Arrays.asList(libro1, libro2));
        when(catalogService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // When & Then: respuesta paginada, links dentro de $.content
        // "content": [{"links": [{"rel":"self","href":"http://localhost/api/catalog/1"}]}]
        mockMvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].links[0].rel").value("self"))
                .andExpect(jsonPath("$.content[0].links[0].href").value(org.hamcrest.Matchers.containsString("/api/catalog/1")))
                .andExpect(jsonPath("$.content[1].links[0].rel").value("self"))
                .andExpect(jsonPath("$.content[1].links[0].href").value(org.hamcrest.Matchers.containsString("/api/catalog/2")));
    }

    @Test
    void obtenerPorId_DebeIncluirTodosLosEnlacesHATEOAS() throws Exception {
        // Given
        LibroResponseDTO libro = crearLibroResponse(1L, "1984", "Orwell");
        when(catalogService.obtenerPorId(1L)).thenReturn(libro);

        // When & Then: respuesta en application/hal+json, links como _links objeto
        // "_links":{"self":{"href":"..."},"todos":{"href":"..."},...}
        mockMvc.perform(get("/api/catalog/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value(org.hamcrest.Matchers.containsString("/api/catalog/1")))
                .andExpect(jsonPath("$._links.todos.href").value(org.hamcrest.Matchers.containsString("/api/catalog")))
                .andExpect(jsonPath("$._links.disponibles.href").value(org.hamcrest.Matchers.containsString("/api/catalog/disponibles")))
                .andExpect(jsonPath("$._links.eliminar.href").value(org.hamcrest.Matchers.containsString("/api/catalog/1")));
    }

    @Test
    void agregar_DebeIncluirEnlacesHATEOAS() throws Exception {
        // Given
        LibroRequestDTO request = crearLibroRequest();
        LibroResponseDTO response = crearLibroResponse(1L, "Nuevo Libro", "Autor Test");
        when(catalogService.agregar(any(LibroRequestDTO.class))).thenReturn(response);

        // When & Then: respuesta en application/hal+json, links como _links objeto
        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$._links.self.href").value(org.hamcrest.Matchers.containsString("/api/catalog/1")))
                .andExpect(jsonPath("$._links.todos.href").value(org.hamcrest.Matchers.containsString("/api/catalog")));
    }

    @Test
    void actualizar_DebeIncluirEnlacesHATEOAS() throws Exception {
        // Given
        LibroRequestDTO request = crearLibroRequest();
        LibroResponseDTO response = crearLibroResponse(1L, "Libro Actualizado", "Autor Test");
        when(catalogService.actualizar(eq(1L), any(LibroRequestDTO.class))).thenReturn(response);

        // When & Then: respuesta en application/hal+json, links como _links objeto
        mockMvc.perform(put("/api/catalog/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value(org.hamcrest.Matchers.containsString("/api/catalog/1")))
                .andExpect(jsonPath("$._links.todos.href").value(org.hamcrest.Matchers.containsString("/api/catalog")));
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
