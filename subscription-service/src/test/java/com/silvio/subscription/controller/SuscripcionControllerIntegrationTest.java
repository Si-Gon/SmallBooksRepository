package com.silvio.subscription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración del SuscripcionController.
 *
 * Verifican que el servicio use exclusivamente el header X-User-Id
 * propagado por el Gateway y rechace peticiones sin él.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuscripcionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void miPlan_conXUserId_debeRetornar404_cuandoNoExiste() throws Exception {
        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("X-User-Id", "usuario_sin_suscripcion"))
                .andExpect(status().isNotFound());
    }

    @Test
    void miPlan_conAuthorizationSinXUserId_debeRetornar400() throws Exception {
        mockMvc.perform(get("/api/subscriptions/mi-plan")
                        .header("Authorization", "Bearer token.fake"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_conXUserId_debeRetornar201() throws Exception {
        SuscripcionRequestDTO request = new SuscripcionRequestDTO();
        request.setPlan(PlanSuscripcion.BASICO);
        request.setMeses(1);

        mockMvc.perform(post("/api/subscriptions")
                        .header("X-User-Id", "nuevo_usuario_integracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value("nuevo_usuario_integracion"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.plan").value("BASICO"));
    }

    @Test
    void crear_conAuthorizationSinXUserId_debeRetornar400() throws Exception {
        SuscripcionRequestDTO request = new SuscripcionRequestDTO();
        request.setPlan(PlanSuscripcion.BASICO);
        request.setMeses(1);

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer token.fake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelar_conXUserIdSinSuscripcion_debeRetornar404() throws Exception {
        mockMvc.perform(patch("/api/subscriptions/cancelar")
                        .header("X-User-Id", "usuario_sin_suscripcion"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelar_sinXUserId_debeRetornar400() throws Exception {
        mockMvc.perform(patch("/api/subscriptions/cancelar"))
                .andExpect(status().isBadRequest());
    }
}
