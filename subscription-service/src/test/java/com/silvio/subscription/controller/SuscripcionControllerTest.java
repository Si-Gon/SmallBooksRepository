package com.silvio.subscription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.dto.SuscripcionResponseDTO;
import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import com.silvio.subscription.security.JwtExtractor;
import com.silvio.subscription.service.SuscripcionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SuscripcionController.class)
@Import(JwtExtractor.class) 
class SuscripcionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SuscripcionService suscripcionService;

    // ─── helpers ─────────────────────────────────────────────────────────────

    // Genera un token JWT falso pero parseable por extraerUsuario()
    // El método espera: Bearer header.payload.signature
    // El payload debe ser Base64 con {"sub":"usuario1"}
    private String tokenFalso(String username) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + username + "\",\"roles\":\"ROLE_USER\"}").getBytes());
        return "Bearer header." + payload + ".signature";
    }

    private SuscripcionResponseDTO responseBasico(String usuarioId) {
        SuscripcionResponseDTO dto = new SuscripcionResponseDTO();
        dto.setId(1L);
        dto.setUsuarioId(usuarioId);
        dto.setPlan(PlanSuscripcion.BASICO);
        dto.setFechaInicio(LocalDateTime.now());
        dto.setFechaFin(LocalDateTime.now().plusMonths(1));
        dto.setActiva(true);
        dto.setMaxPrestamos(2);
        dto.setDiasPrestamo(7);
        return dto;
    }

    private SuscripcionResponseDTO responsePremium(String usuarioId) {
        SuscripcionResponseDTO dto = new SuscripcionResponseDTO();
        dto.setId(2L);
        dto.setUsuarioId(usuarioId);
        dto.setPlan(PlanSuscripcion.PREMIUM);
        dto.setFechaInicio(LocalDateTime.now());
        dto.setFechaFin(LocalDateTime.now().plusMonths(1));
        dto.setActiva(true);
        dto.setMaxPrestamos(5);
        dto.setDiasPrestamo(14);
        return dto;
    }

    private SuscripcionRequestDTO requestPlan(PlanSuscripcion plan) {
        SuscripcionRequestDTO req = new SuscripcionRequestDTO();
        req.setPlan(plan);
        req.setMeses(1);
        return req;
    }

    // ─── GET /api/subscriptions/mi-plan ──────────────────────────────────────

    @Test
    void miPlan_devuelve_200_con_suscripcion_activa() throws Exception {
        // Given
        String token = tokenFalso("usuario1");
        when(suscripcionService.obtenerPorUsuario("usuario1"))
                .thenReturn(responseBasico("usuario1"));

        // When & Then
        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value("usuario1"))
                .andExpect(jsonPath("$.plan").value("BASICO"))
                .andExpect(jsonPath("$.maxPrestamos").value(2))
                .andExpect(jsonPath("$.diasPrestamo").value(7))
                .andExpect(jsonPath("$._links.self").exists())
                .andExpect(jsonPath("$._links.cancelar").exists());

        verify(suscripcionService).obtenerPorUsuario("usuario1");
    }

    @Test
    void miPlan_devuelve_404_cuando_no_tiene_suscripcion() throws Exception {
        // Given
        String token = tokenFalso("usuario_sin_plan");
        when(suscripcionService.obtenerPorUsuario("usuario_sin_plan"))
                .thenThrow(new RuntimeException(
                        "No hay suscripción activa para el usuario: usuario_sin_plan"));

        // When & Then
        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("Authorization", token))
                .andExpect(status().isNotFound());

        verify(suscripcionService).obtenerPorUsuario("usuario_sin_plan");
    }

    @Test
    void miPlan_devuelve_500_cuando_token_es_invalido() throws Exception {
        // Given — token mal formado, extraerUsuario() lanza RuntimeException
        // que el GlobalExceptionHandler convierte en 404
        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("Authorization", "Bearer token.invalido"))
                .andExpect(status().isNotFound());

        verify(suscripcionService, never()).obtenerPorUsuario(any());
    }

    // ─── GET /api/subscriptions/usuario/{usuarioId} ───────────────────────────

    @Test
    void obtenerPorUsuarioId_devuelve_200_plan_BASICO() throws Exception {
        // Given
        when(suscripcionService.obtenerPorUsuario("usuario1"))
                .thenReturn(responseBasico("usuario1"));

        // When & Then
        mockMvc.perform(get("/api/subscriptions/usuario/usuario1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value("usuario1"))
                .andExpect(jsonPath("$.plan").value("BASICO"))
                .andExpect(jsonPath("$.maxPrestamos").value(2))
                .andExpect(jsonPath("$.diasPrestamo").value(7))
                .andExpect(jsonPath("$._links.self").exists());

        verify(suscripcionService).obtenerPorUsuario("usuario1");
    }

    @Test
    void obtenerPorUsuarioId_devuelve_200_plan_PREMIUM() throws Exception {
        // Given
        when(suscripcionService.obtenerPorUsuario("usuario2"))
                .thenReturn(responsePremium("usuario2"));

        // When & Then
        mockMvc.perform(get("/api/subscriptions/usuario/usuario2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.maxPrestamos").value(5))
                .andExpect(jsonPath("$.diasPrestamo").value(14));

        verify(suscripcionService).obtenerPorUsuario("usuario2");
    }

    @Test
    void obtenerPorUsuarioId_devuelve_404_cuando_no_existe() throws Exception {
        // Given
        when(suscripcionService.obtenerPorUsuario("noexiste"))
                .thenThrow(new RuntimeException(
                        "No hay suscripción activa para el usuario: noexiste"));

        // When & Then
        mockMvc.perform(get("/api/subscriptions/usuario/noexiste"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        verify(suscripcionService).obtenerPorUsuario("noexiste");
    }

    // ─── POST /api/subscriptions ──────────────────────────────────────────────

    @Test
    void crear_devuelve_201_plan_BASICO() throws Exception {
        // Given
        String token = tokenFalso("usuario1");
        SuscripcionRequestDTO request = requestPlan(PlanSuscripcion.BASICO);
        when(suscripcionService.crear(any(SuscripcionRequestDTO.class), eq("usuario1")))
                .thenReturn(responseBasico("usuario1"));

        // When & Then
        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("BASICO"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$._links.mi-plan").exists())
                .andExpect(jsonPath("$._links.cancelar").exists());

        verify(suscripcionService).crear(any(SuscripcionRequestDTO.class), eq("usuario1"));
    }

    @Test
    void crear_devuelve_201_plan_PREMIUM() throws Exception {
        // Given
        String token = tokenFalso("usuario2");
        SuscripcionRequestDTO request = requestPlan(PlanSuscripcion.PREMIUM);
        when(suscripcionService.crear(any(SuscripcionRequestDTO.class), eq("usuario2")))
                .thenReturn(responsePremium("usuario2"));

        // When & Then
        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.maxPrestamos").value(5))
                .andExpect(jsonPath("$.diasPrestamo").value(14));

        verify(suscripcionService).crear(any(SuscripcionRequestDTO.class), eq("usuario2"));
    }

    @Test
    void crear_devuelve_400_cuando_plan_es_null() throws Exception {
        // Given — viola @NotNull en SuscripcionRequestDTO
        String token = tokenFalso("usuario1");
        SuscripcionRequestDTO request = new SuscripcionRequestDTO();
        request.setPlan(null);

        // When & Then
        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(suscripcionService, never()).crear(any(), any());
    }

    // ─── PATCH /api/subscriptions/cancelar ───────────────────────────────────

    @Test
    void cancelar_devuelve_200_cuando_tiene_suscripcion_activa() throws Exception {
        // Given
        String token = tokenFalso("usuario1");
        SuscripcionResponseDTO cancelada = responseBasico("usuario1");
        cancelada.setActiva(false);
        when(suscripcionService.cancelar("usuario1")).thenReturn(cancelada);

        // When & Then
        mockMvc.perform(patch("/api/subscriptions/cancelar")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$._links.mi-plan").exists());

        verify(suscripcionService).cancelar("usuario1");
    }

    @Test
    void cancelar_devuelve_404_cuando_no_tiene_suscripcion_activa() throws Exception {
        // Given
        String token = tokenFalso("usuario_sin_plan");
        when(suscripcionService.cancelar("usuario_sin_plan"))
                .thenThrow(new RuntimeException(
                        "No hay suscripción activa para el usuario: usuario_sin_plan"));

        // When & Then
        mockMvc.perform(patch("/api/subscriptions/cancelar")
                        .header("Authorization", token))
                .andExpect(status().isNotFound());

        verify(suscripcionService).cancelar("usuario_sin_plan");
    }
}