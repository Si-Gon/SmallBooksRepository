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
 * Problema original: el test anterior NO mockeaba JwtExtractor, que es un
 * @Component inyectado en el controller. @WebMvcTest carga el controller y
 * sus dependencias directas — si JwtExtractor no está mockeado, Spring no
 * puede crear el contexto y TODOS los tests se saltan (0 ejecutados, 0 fallos,
 * pero tampoco hay cobertura).
 *
 * Solución: agregar @MockBean JwtExtractor y configurar su comportamiento
 * en cada test con when(jwtExtractor.extraerUsuario(...)).thenReturn("usuario").
 *
 * Estrategia del token falso:
 * - El token real que usa JwtExtractor tiene el formato: Bearer header.payload.sig
 *   donde payload es Base64({"sub":"usuario"})
 * - En los tests mockeamos directamente JwtExtractor, así que el formato
 *   del token no importa — solo nos interesa el valor que devuelve el mock.
 * - Usamos "Bearer fake.token" como valor constante y configuramos el mock
 *   para que devuelva el usuario correspondiente a ese header.
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

    // ← Esta es la clave que faltaba en el test original
    @MockBean
    private JwtExtractor jwtExtractor;

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
        when(jwtExtractor.extraerUsuario("Bearer fake.token")).thenReturn("silvio");
        when(suscripcionService.obtenerPorUsuario("silvio")).thenReturn(responseBasico("silvio"));

        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("Authorization", "Bearer fake.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value("silvio"))
                .andExpect(jsonPath("$.plan").value("BASICO"))
                .andExpect(jsonPath("$.maxPrestamos").value(2))
                .andExpect(jsonPath("$.diasPrestamo").value(7));

        verify(suscripcionService).obtenerPorUsuario("silvio");
    }

    @Test
    void miPlan_devuelve404_cuandoNoTieneSuscripcion() throws Exception {
        when(jwtExtractor.extraerUsuario("Bearer fake.token")).thenReturn("usuario_sin_plan");
        when(suscripcionService.obtenerPorUsuario("usuario_sin_plan"))
                .thenThrow(new RuntimeException("No hay suscripción activa para el usuario: usuario_sin_plan"));

        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("Authorization", "Bearer fake.token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void miPlan_devuelve404_cuandoTokenEsInvalido() throws Exception {
        // JwtExtractor lanza excepción con token malformado
        when(jwtExtractor.extraerUsuario("Bearer token.invalido"))
                .thenThrow(new RuntimeException("No se pudo extraer el usuario del token"));

        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("Authorization", "Bearer token.invalido"))
                .andExpect(status().isNotFound());

        verify(suscripcionService, never()).obtenerPorUsuario(any());
    }

    // ─── GET /api/subscriptions/usuario/{usuarioId} ───────────────────────────

    @Test
    void obtenerPorUsuarioId_devuelve200_planBasico() throws Exception {
        when(suscripcionService.obtenerPorUsuario("silvio")).thenReturn(responseBasico("silvio"));

        mockMvc.perform(get("/api/subscriptions/usuario/silvio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value("silvio"))
                .andExpect(jsonPath("$.plan").value("BASICO"))
                .andExpect(jsonPath("$.maxPrestamos").value(2))
                .andExpect(jsonPath("$.diasPrestamo").value(7));
    }

    @Test
    void obtenerPorUsuarioId_devuelve200_planPremium() throws Exception {
        when(suscripcionService.obtenerPorUsuario("ana")).thenReturn(responsePremium("ana"));

        mockMvc.perform(get("/api/subscriptions/usuario/ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.maxPrestamos").value(5))
                .andExpect(jsonPath("$.diasPrestamo").value(14));
    }

    @Test
    void obtenerPorUsuarioId_devuelve404_cuandoNoExiste() throws Exception {
        when(suscripcionService.obtenerPorUsuario("noexiste"))
                .thenThrow(new RuntimeException("No hay suscripción activa para el usuario: noexiste"));

        mockMvc.perform(get("/api/subscriptions/usuario/noexiste"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─── POST /api/subscriptions ──────────────────────────────────────────────

    @Test
    void crear_devuelve201_planBasico() throws Exception {
        when(jwtExtractor.extraerUsuario("Bearer fake.token")).thenReturn("silvio");
        when(suscripcionService.crear(any(SuscripcionRequestDTO.class), eq("silvio")))
                .thenReturn(responseBasico("silvio"));

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer fake.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPlan(PlanSuscripcion.BASICO, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("BASICO"))
                .andExpect(jsonPath("$.activa").value(true));

        verify(suscripcionService).crear(any(SuscripcionRequestDTO.class), eq("silvio"));
    }

    @Test
    void crear_devuelve201_planPremium() throws Exception {
        when(jwtExtractor.extraerUsuario("Bearer fake.token")).thenReturn("ana");
        when(suscripcionService.crear(any(SuscripcionRequestDTO.class), eq("ana")))
                .thenReturn(responsePremium("ana"));

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer fake.token")
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
                        .header("Authorization", "Bearer fake.token")
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

        when(jwtExtractor.extraerUsuario("Bearer fake.token")).thenReturn("silvio");
        when(suscripcionService.cancelar("silvio")).thenReturn(cancelada);

        mockMvc.perform(patch("/api/subscriptions/cancelar")
                        .header("Authorization", "Bearer fake.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));

        verify(suscripcionService).cancelar("silvio");
    }

    @Test
    void cancelar_devuelve404_cuandoNoTieneSuscripcion() throws Exception {
        when(jwtExtractor.extraerUsuario("Bearer fake.token")).thenReturn("usuario_sin_plan");
        when(suscripcionService.cancelar("usuario_sin_plan"))
                .thenThrow(new RuntimeException("No hay suscripción activa para el usuario: usuario_sin_plan"));

        mockMvc.perform(patch("/api/subscriptions/cancelar")
                        .header("Authorization", "Bearer fake.token"))
                .andExpect(status().isNotFound());
    }
}
