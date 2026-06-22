package com.silvio.license.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.license.dto.LicenseRequestDTO;
import com.silvio.license.dto.LicenseResponseDTO;
import com.silvio.license.service.LicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LicenseController.class)
class LicenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LicenseService licenseService;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private LicenseResponseDTO licenseResponse(Long libroId, int total, int disponibles) {
        LicenseResponseDTO dto = new LicenseResponseDTO();
        dto.setId(1L);
        dto.setLibroId(libroId);
        dto.setTotalCopias(total);
        dto.setCopiasDisponibles(disponibles);
        return dto;
    }

    private LicenseRequestDTO licenseRequest(Long libroId, int totalCopias) {
        LicenseRequestDTO req = new LicenseRequestDTO();
        req.setLibroId(libroId);
        req.setTotalCopias(totalCopias);
        return req;
    }

    // ─── GET /api/licenses ───────────────────────────────────────────────────

    @Test
    void obtenerTodas_devuelve_200_con_lista() throws Exception {
        // Given
        List<LicenseResponseDTO> lista = Arrays.asList(
                licenseResponse(1L, 5, 3),
                licenseResponse(2L, 10, 8)
        );
        when(licenseService.obtenerTodas()).thenReturn(lista);

        // When & Then
        mockMvc.perform(get("/api/licenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].libroId").value(1))
                .andExpect(jsonPath("$[1].libroId").value(2));

        verify(licenseService).obtenerTodas();
    }

    @Test
    void obtenerTodas_devuelve_200_con_lista_vacia() throws Exception {
        // Given
        when(licenseService.obtenerTodas()).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/licenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── GET /api/licenses/{libroId} ─────────────────────────────────────────

    @Test
    void obtenerPorLibroId_devuelve_200_cuando_existe() throws Exception {
        // Given
        when(licenseService.obtenerPorLibroId(1L)).thenReturn(licenseResponse(1L, 5, 3));

        // When & Then
        mockMvc.perform(get("/api/licenses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libroId").value(1))
                .andExpect(jsonPath("$.totalCopias").value(5))
                .andExpect(jsonPath("$.copiasDisponibles").value(3))
                .andExpect(jsonPath("$._links.self").exists())
                .andExpect(jsonPath("$._links.prestar").exists())
                .andExpect(jsonPath("$._links.devolver").exists());

        verify(licenseService).obtenerPorLibroId(1L);
    }

    @Test
    void obtenerPorLibroId_devuelve_404_cuando_no_existe() throws Exception {
        // Given
        when(licenseService.obtenerPorLibroId(999L))
            .thenThrow(new RuntimeException("No existe licencia para el libro con id: 999"));

        // When & Then
        mockMvc.perform(get("/api/licenses/999"))
            .andExpect(status().isNotFound());  // 404 — default del GlobalExceptionHandler

        verify(licenseService).obtenerPorLibroId(999L);
    }


    // ─── POST /api/licenses ──────────────────────────────────────────────────

    @Test
    void crear_devuelve_201_con_datos_validos() throws Exception {
        // Given
        LicenseRequestDTO request = licenseRequest(3L, 10);
        LicenseResponseDTO response = licenseResponse(3L, 10, 10);
        when(licenseService.crear(any(LicenseRequestDTO.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libroId").value(3))
                .andExpect(jsonPath("$.totalCopias").value(10))
                .andExpect(jsonPath("$.copiasDisponibles").value(10));

        verify(licenseService).crear(any(LicenseRequestDTO.class));
    }

    @Test
    void crear_devuelve_400_cuando_libroId_es_null() throws Exception {
        // Given — request sin libroId (viola @NotNull)
        LicenseRequestDTO request = new LicenseRequestDTO();
        request.setTotalCopias(5); // libroId queda null

        // When & Then
        mockMvc.perform(post("/api/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(licenseService, never()).crear(any());
    }

    @Test
    void crear_devuelve_400_cuando_totalCopias_es_cero() throws Exception {
        // Given — viola @Min(value = 1)
        LicenseRequestDTO request = new LicenseRequestDTO();
        request.setLibroId(1L);
        request.setTotalCopias(0);

        // When & Then
        mockMvc.perform(post("/api/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(licenseService, never()).crear(any());
    }

    // ─── PUT /api/licenses/{libroId} ─────────────────────────────────────────

    @Test
    void actualizar_devuelve_200_con_datos_validos() throws Exception {
        // Given
        LicenseRequestDTO request = licenseRequest(1L, 20);
        LicenseResponseDTO response = licenseResponse(1L, 20, 18);
        when(licenseService.actualizar(eq(1L), any(LicenseRequestDTO.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/licenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopias").value(20))
                .andExpect(jsonPath("$.copiasDisponibles").value(18));

        verify(licenseService).actualizar(eq(1L), any(LicenseRequestDTO.class));
    }

    // ─── PUT /api/licenses/{libroId}/prestar ─────────────────────────────────

    @Test
    void prestar_devuelve_200_cuando_hay_copias_disponibles() throws Exception {
        // Given
        LicenseResponseDTO response = licenseResponse(1L, 5, 2); // quedaron 2
        when(licenseService.prestar(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/licenses/1/prestar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copiasDisponibles").value(2))
                .andExpect(jsonPath("$._links.devolver").exists());

        verify(licenseService).prestar(1L);
    }

    @Test
    void prestar_devuelve_422_cuando_no_hay_copias() throws Exception {
        // Given
        when(licenseService.prestar(1L))
            .thenThrow(new RuntimeException("No hay copias disponibles del libro con id: 1"));

        // When & Then
        mockMvc.perform(put("/api/licenses/1/prestar"))
            .andExpect(status().isUnprocessableEntity());  // 422

        verify(licenseService).prestar(1L);
}

    // ─── PUT /api/licenses/{libroId}/devolver ────────────────────────────────

    @Test
    void devolver_devuelve_200_cuando_hay_copias_prestadas() throws Exception {
        // Given
        LicenseResponseDTO response = licenseResponse(1L, 5, 4); // devolvió 1
        when(licenseService.devolver(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/licenses/1/devolver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copiasDisponibles").value(4))
                .andExpect(jsonPath("$._links.prestar").exists());

        verify(licenseService).devolver(1L);
    }

    @Test
     void devolver_devuelve_404_cuando_todas_las_copias_estan_disponibles() throws Exception {
        // Given
        when(licenseService.devolver(1L))
            .thenThrow(new RuntimeException("Todas las copias del libro ya están disponibles"));

        // When & Then
        mockMvc.perform(put("/api/licenses/1/devolver"))
            .andExpect(status().isNotFound());  // 404 — default del GlobalExceptionHandler

        verify(licenseService).devolver(1L);
     }
}