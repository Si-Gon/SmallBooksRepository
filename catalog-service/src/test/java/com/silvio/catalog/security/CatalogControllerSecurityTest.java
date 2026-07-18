package com.silvio.catalog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.dto.LibroResponseDTO;
import com.silvio.catalog.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración que verifican el RBAC completo en CatalogController.
 *
 * A diferencia de CatalogControllerTest (que usa excludeFilters + TestSecurityConfig
 * para desactivar la seguridad), este test carga el SecurityConfig REAL y el
 * JwtAuthenticationFilter REAL, probando el flujo completo:
 *   1. Se envía la petición HTTP con header X-User-Roles
 *   2. JwtAuthenticationFilter lee el header y establece la autenticación
 *   3. SecurityConfig aplica hasRole('ADMIN') en endpoints de escritura
 *   4. @PreAuthorize en el controller actúa como segunda capa de defensa
 *
 * CatalogService se mockea para evitar dependencia con la base de datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CatalogService catalogService;

    // =========================================================
    // POST /api/catalog  — solo ROLE_ADMIN
    // =========================================================

    @Test
    void post_ConRolUser_debeRetornar403() throws Exception {
        // Given — un libro válido pero el usuario solo tiene ROLE_USER
        LibroRequestDTO request = crearLibroRequest();

        // When & Then — 403 Forbidden por falta de ROLE_ADMIN
        mockMvc.perform(post("/api/catalog")
                        .header("X-User-Roles", "ROLE_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // El servicio no debe ser invocado
        verify(catalogService, never()).agregar(any());
    }

    @Test
    void post_ConRolAdmin_debeRetornar201() throws Exception {
        // Given — un libro válido con usuario administrador
        LibroRequestDTO request = crearLibroRequest();
        LibroResponseDTO response = new LibroResponseDTO();
        response.setId(1L);
        response.setTitulo("Nuevo Libro");
        when(catalogService.agregar(any())).thenReturn(response);

        // When & Then — 201 Created, el servicio fue invocado
        mockMvc.perform(post("/api/catalog")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Nuevo Libro"));

        verify(catalogService).agregar(any());
    }

    @Test
    void post_SinHeaderRoles_debeRetornar403() throws Exception {
        // Given — un libro válido pero sin header de autenticación
        LibroRequestDTO request = crearLibroRequest();

        // When & Then — 403 porque anyRequest().authenticated() bloquea
        mockMvc.perform(post("/api/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(catalogService, never()).agregar(any());
    }

    // =========================================================
    // PUT /api/catalog/{id}  — solo ROLE_ADMIN
    // =========================================================

    @Test
    void put_ConRolUser_debeRetornar403() throws Exception {
        // Given — datos de actualización con usuario normal
        LibroRequestDTO request = crearLibroRequest();

        // When & Then
        mockMvc.perform(put("/api/catalog/1")
                        .header("X-User-Roles", "ROLE_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(catalogService, never()).actualizar(any(), any());
    }

    @Test
    void put_ConRolAdmin_debeRetornar200() throws Exception {
        // Given — datos de actualización con administrador
        LibroRequestDTO request = crearLibroRequest();
        LibroResponseDTO response = new LibroResponseDTO();
        response.setId(1L);
        response.setTitulo("Libro Actualizado");
        when(catalogService.actualizar(any(), any())).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/catalog/1")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Libro Actualizado"));

        verify(catalogService).actualizar(any(), any());
    }

    // =========================================================
    // PATCH /api/catalog/{id}/disponibilidad  — solo ROLE_ADMIN
    // =========================================================

    @Test
    void patch_ConRolUser_debeRetornar403() throws Exception {
        // When & Then
        mockMvc.perform(patch("/api/catalog/1/disponibilidad")
                        .header("X-User-Roles", "ROLE_USER")
                        .param("disponible", "true"))
                .andExpect(status().isForbidden());

        verify(catalogService, never()).cambiarDisponibilidad(any(), anyBoolean());
    }

    @Test
    void patch_ConRolAdmin_debeRetornar200() throws Exception {
        // Given
        LibroResponseDTO response = new LibroResponseDTO();
        response.setId(1L);
        response.setDisponible(false);
        when(catalogService.cambiarDisponibilidad(any(), anyBoolean())).thenReturn(response);

        // When & Then
        mockMvc.perform(patch("/api/catalog/1/disponibilidad")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .param("disponible", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false));

        verify(catalogService).cambiarDisponibilidad(any(), anyBoolean());
    }

    // =========================================================
    // DELETE /api/catalog/{id}  — solo ROLE_ADMIN
    // =========================================================

    @Test
    void delete_ConRolUser_debeRetornar403() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/catalog/1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isForbidden());

        verify(catalogService, never()).eliminar(any());
    }

    @Test
    void delete_ConRolAdmin_debeRetornar204() throws Exception {
        // Given
        doNothing().when(catalogService).eliminar(1L);

        // When & Then
        mockMvc.perform(delete("/api/catalog/1")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isNoContent());

        verify(catalogService).eliminar(1L);
    }

    // =========================================================
    // GET /api/catalog  — acceso público (permitAll)
    // =========================================================

    @Test
    void get_SinAuth_debeRetornar200() throws Exception {
        // Given — sin headers de autenticación
        when(catalogService.obtenerTodos(any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList()));

        // When & Then
        mockMvc.perform(get("/api/catalog"))
                .andExpect(status().isOk());

        verify(catalogService).obtenerTodos(any());
    }

    @Test
    void getDisponibles_SinAuth_debeRetornar200() throws Exception {
        // Given — sin headers de autenticación
        when(catalogService.obtenerDisponibles()).thenReturn(java.util.Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/catalog/disponibles"))
                .andExpect(status().isOk());

        verify(catalogService).obtenerDisponibles();
    }

    @Test
    void getPorId_SinAuth_debeRetornar200() throws Exception {
        // Given — sin headers de autenticación
        LibroResponseDTO libro = new LibroResponseDTO();
        libro.setId(1L);
        libro.setTitulo("Libro Público");
        when(catalogService.obtenerPorId(1L)).thenReturn(libro);

        // When & Then
        mockMvc.perform(get("/api/catalog/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Libro Público"));

        verify(catalogService).obtenerPorId(1L);
    }

    // =========================================================
    // Casos borde — rol ADMIN via múltiples roles
    // =========================================================

    @Test
    void post_ConMultiplesRolesIncluyendoAdmin_debeRetornar201() throws Exception {
        // Given — usuario con múltiples roles incluyendo ADMIN
        LibroRequestDTO request = crearLibroRequest();
        LibroResponseDTO response = new LibroResponseDTO();
        response.setId(1L);
        response.setTitulo("Libro Admin");
        when(catalogService.agregar(any())).thenReturn(response);

        // When & Then — "ROLE_USER,ROLE_ADMIN" incluye ROLE_ADMIN → autorizado
        mockMvc.perform(post("/api/catalog")
                        .header("X-User-Roles", "ROLE_USER,ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Libro Admin"));

        verify(catalogService).agregar(any());
    }

    // =========================================================
    // Helper
    // =========================================================

    private LibroRequestDTO crearLibroRequest() {
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro de Prueba");
        request.setAutor("Autor Test");
        request.setIsbn("1234567890123");
        request.setEditorial("Editorial Test");
        request.setAnioPublicacion(2024);
        request.setIdioma("Español");
        request.setGenero("Ficción");
        return request;
    }
}
