package com.silvio.subscription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.dto.SuscripcionResponseDTO;
import com.silvio.subscription.exception.SuscripcionNotFoundException;
import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import com.silvio.subscription.service.SuscripcionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del SuscripcionController.
 *
 * El usuario se identifica desde el header X-User-Id propagado por el Gateway.
 * No se requiere ni se valida token JWT en este microservicio.
 */
@WebMvcTest(SuscripcionController.class)
@ActiveProfiles("test")
class SuscripcionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SuscripcionService suscripcionService;

    // ─── helpers ──────────────────────────────────────────────────────────────

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
        dto.setFechaFin(LocalDateTime.now().plusMonths(6));
        dto.setActiva(true);
        dto.setMaxPrestamos(5);
        dto.setDiasPrestamo(14);
        return dto;
    }

    private SuscripcionRequestDTO requestPlan(PlanSuscripcion plan, int meses) {
        SuscripcionRequestDTO req = new SuscripcionRequestDTO();
        req.setPlan(plan);
        req.setMeses(meses);
        return req;
    }

    // ─── GET /api/subscriptions/mi-plan ──────────────────────────────────────

    @Test
    void miPlan_devuelve200_conSuscripcionActiva() throws Exception {
        when(suscripcionService.obtenerPorUsuario("silvio")).thenReturn(responseBasico("silvio"));

        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("X-User-Id", "silvio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value("silvio"))
                .andExpect(jsonPath("$.plan").value("BASICO"))
                .andExpect(jsonPath("$.maxPrestamos").value(2))
                .andExpect(jsonPath("$.diasPrestamo").value(7));

        verify(suscripcionService).obtenerPorUsuario("silvio");
    }

    @Test
    void miPlan_devuelve404_cuandoNoTieneSuscripcion() throws Exception {
        when(suscripcionService.obtenerPorUsuario("usuario_sin_plan"))
                .thenThrow(new SuscripcionNotFoundException("usuario_sin_plan"));

        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("X-User-Id", "usuario_sin_plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void miPlan_devuelve400_cuandoHeaderXUserIdAusente() throws Exception {
        mockMvc.perform(get("/api/subscriptions/mi-plan"))
                .andExpect(status().isBadRequest());

        verify(suscripcionService, never()).obtenerPorUsuario(any());
    }

    // ─── GET /api/subscriptions/usuario/{usuarioId} ───────────────────────────

    @Test
    void obtenerPorUsuarioId_devuelve200_planBasico() throws Exception {
        when(suscripcionService.obtenerPorUsuario("silvio")).thenReturn(responseBasico("silvio"));

        mockMvc.perform(get("/api/subscriptions/usuario/silvio")
                        .header("X-User-Id", "silvio")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value("silvio"))
                .andExpect(jsonPath("$.plan").value("BASICO"))
                .andExpect(jsonPath("$.maxPrestamos").value(2))
                .andExpect(jsonPath("$.diasPrestamo").value(7));
    }

    @Test
    void obtenerPorUsuarioId_devuelve200_planPremium() throws Exception {
        when(suscripcionService.obtenerPorUsuario("ana")).thenReturn(responsePremium("ana"));

        mockMvc.perform(get("/api/subscriptions/usuario/ana")
                        .header("X-User-Id", "ana")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.maxPrestamos").value(5))
                .andExpect(jsonPath("$.diasPrestamo").value(14));
    }

    @Test
    void obtenerPorUsuarioId_devuelve404_cuandoNoExiste() throws Exception {
        when(suscripcionService.obtenerPorUsuario("noexiste"))
                .thenThrow(new SuscripcionNotFoundException("noexiste"));

        mockMvc.perform(get("/api/subscriptions/usuario/noexiste")
                        .header("X-User-Id", "noexiste")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─── IDOR: validación de acceso a datos de otro usuario ──────────────────

    @Test
    void obtenerPorUsuarioId_cuando_mismoUsuario_devuelve200() throws Exception {
        // Given
        when(suscripcionService.obtenerPorUsuario("silvio")).thenReturn(responseBasico("silvio"));

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(get("/api/subscriptions/usuario/silvio")
                        .header("X-User-Id", "silvio")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk());

        verify(suscripcionService).obtenerPorUsuario("silvio");
    }

    @Test
    void obtenerPorUsuarioId_cuando_otroUsuario_devuelve403() throws Exception {
        // Given — X-User-Id ("otro") NO coincide con {usuarioId} ("silvio")

        // When & Then
        mockMvc.perform(get("/api/subscriptions/usuario/silvio")
                        .header("X-User-Id", "otro")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(suscripcionService, never()).obtenerPorUsuario(anyString());
    }

    @Test
    void obtenerPorUsuarioId_cuando_admin_devuelve200() throws Exception {
        // Given
        when(suscripcionService.obtenerPorUsuario("silvio")).thenReturn(responseBasico("silvio"));

        // When & Then — ROLE_ADMIN puede acceder a cualquier usuarioId
        mockMvc.perform(get("/api/subscriptions/usuario/silvio")
                        .header("X-User-Id", "admin")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());

        verify(suscripcionService).obtenerPorUsuario("silvio");
    }

    @Test
    void obtenerPorUsuarioId_cuando_headerAusente_devuelve403() throws Exception {
        // Given — sin header X-User-Id

        // When & Then
        mockMvc.perform(get("/api/subscriptions/usuario/silvio"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(suscripcionService, never()).obtenerPorUsuario(anyString());
    }

    // ─── POST /api/subscriptions ──────────────────────────────────────────────

    @Test
    void crear_devuelve201_planBasico() throws Exception {
        when(suscripcionService.crear(any(SuscripcionRequestDTO.class), eq("silvio")))
                .thenReturn(responseBasico("silvio"));

        mockMvc.perform(post("/api/subscriptions")
                        .header("X-User-Id", "silvio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPlan(PlanSuscripcion.BASICO, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("BASICO"))
                .andExpect(jsonPath("$.activa").value(true));

        verify(suscripcionService).crear(any(SuscripcionRequestDTO.class), eq("silvio"));
    }

    @Test
    void crear_devuelve201_planPremium() throws Exception {
        when(suscripcionService.crear(any(SuscripcionRequestDTO.class), eq("ana")))
                .thenReturn(responsePremium("ana"));

        mockMvc.perform(post("/api/subscriptions")
                        .header("X-User-Id", "ana")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPlan(PlanSuscripcion.PREMIUM, 6))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.maxPrestamos").value(5));
    }

    @Test
    void crear_devuelve400_cuandoPlanEsNull() throws Exception {
        // @NotNull en SuscripcionRequestDTO — Spring rechaza antes de llegar al controller
        SuscripcionRequestDTO request = new SuscripcionRequestDTO();
        request.setPlan(null);

        mockMvc.perform(post("/api/subscriptions")
                        .header("X-User-Id", "silvio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(suscripcionService, never()).crear(any(), any());
    }

    // ─── PATCH /api/subscriptions/cancelar ───────────────────────────────────

    @Test
    void cancelar_devuelve200_cuandoTieneSuscripcionActiva() throws Exception {
        SuscripcionResponseDTO cancelada = responseBasico("silvio");
        cancelada.setActiva(false);

        when(suscripcionService.cancelar("silvio")).thenReturn(cancelada);

        mockMvc.perform(patch("/api/subscriptions/cancelar")
                        .header("X-User-Id", "silvio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));

        verify(suscripcionService).cancelar("silvio");
    }

    @Test
    void cancelar_devuelve404_cuandoNoTieneSuscripcion() throws Exception {
        when(suscripcionService.cancelar("usuario_sin_plan"))
                .thenThrow(new SuscripcionNotFoundException("usuario_sin_plan"));

        mockMvc.perform(patch("/api/subscriptions/cancelar")
                        .header("X-User-Id", "usuario_sin_plan"))
                .andExpect(status().isNotFound());
    }
}
