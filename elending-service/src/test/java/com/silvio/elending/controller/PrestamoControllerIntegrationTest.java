package com.silvio.elending.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.elending.client.LicenseClient;
import com.silvio.elending.client.SubscriptionClient;
import com.silvio.elending.dto.PrestamoRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración del PrestamoController.
 *
 * Verifican el flujo end-to-end del header X-User-Id propagado por el Gateway:
 * - Los endpoints autenticados requieren el header X-User-Id.
 * - El header Authorization ya no se procesa en este microservicio.
 * - Si un cliente malicioso llega directamente con X-User-Id arbitrario,
 *   el servicio lo acepta por diseño (la seguridad está en el Gateway).
 *
 * Con la actualización de SecurityConfig, los tests también envían X-User-Roles
 * para pasar la autorización de Spring Security (hasRole("USER")) y llegar al controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrestamoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LicenseClient licenseClient;

    @MockBean
    private SubscriptionClient subscriptionClient;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private static final String USUARIO_ID = "usuario_integracion";

    @Test
    void obtenerActivos_conXUserId_debeRetornar200() throws Exception {
        mockMvc.perform(get("/api/lending/prestamos/activos")
                        .header("X-User-Id", USUARIO_ID)
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void obtenerActivos_conAuthorizationSinXUserId_debeRetornar400() throws Exception {
        // El servicio ya no extrae el usuario del JWT; sin X-User-Id debe fallar.
        // Se agrega X-User-Roles para pasar la autorización de Spring Security
        // y llegar al controller, que valida la presencia de X-User-Id.
        mockMvc.perform(get("/api/lending/prestamos/activos")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.fake")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obtenerActivos_conXUserIdMalicioso_debeSerAceptado() throws Exception {
        // El Gateway es el único responsable de validar el JWT y sobrescribir X-User-Id.
        // Un acceso directo al microservicio con un header arbitrario es aceptado por diseño.
        mockMvc.perform(get("/api/lending/prestamos/activos")
                        .header("X-User-Id", "hacker_directo")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void obtenerHistorial_conXUserId_debeRetornar200() throws Exception {
        mockMvc.perform(get("/api/lending/prestamos/historial")
                        .header("X-User-Id", USUARIO_ID)
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void crearPrestamo_conXUserIdInvalido_debeRetornar400() throws Exception {
        // Body inválido (libroId nulo) → la validación rechaza antes de llamar al servicio.
        PrestamoRequestDTO request = new PrestamoRequestDTO();

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("X-User-Id", USUARIO_ID)
                        .header("X-User-Roles", "ROLE_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearPrestamo_conAuthorizationSinXUserId_debeRetornar400() throws Exception {
        // Se agrega X-User-Roles para pasar la autorización de Spring Security
        // y llegar al controller, que valida la presencia de X-User-Id.
        PrestamoRequestDTO request = new PrestamoRequestDTO();
        request.setLibroId(1L);

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("Authorization", "Bearer token.valido.aqui")
                        .header("X-User-Roles", "ROLE_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
