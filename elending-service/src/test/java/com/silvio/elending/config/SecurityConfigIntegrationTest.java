package com.silvio.elending.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.elending.client.LicenseClient;
import com.silvio.elending.client.SubscriptionClient;
import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import com.silvio.elending.service.PrestamoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración de SecurityConfig — verifican las reglas de autorización
 * con los filtros de seguridad HABILITADOS (comportamiento real).
 *
 * A diferencia de PrestamoControllerTest (que usa addFilters = false), estos tests
 * pasan por la cadena completa de filtros: JwtAuthenticationFilter → SecurityFilterChain
 * → Controller. Esto permite verificar que:
 *
 * 1. Endpoints públicos (actuator, swagger) no requieren autenticación
 * 2. Endpoints protegidos sin roles → 403 Forbidden
 * 3. Endpoints protegidos con rol incorrecto → 403 Forbidden
 * 4. GET /api/lending/prestamos/todos requiere ROLE_ADMIN
 * 5. Endpoints de usuario requieren ROLE_USER
 * 6. El endpoint IDOR /historial/{usuarioId} llega al controller con ROLE_USER
 *
 * Los servicios se mockean para aislar la prueba de seguridad de la lógica de negocio.
 */
@SpringBootTest
@AutoConfigureMockMvc // filtros habilitados por defecto (addFilters = true)
@ActiveProfiles("test")
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PrestamoService prestamoService;

    @MockBean
    private LicenseClient licenseClient;

    @MockBean
    private SubscriptionClient subscriptionClient;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    // =========================================================
    // Endpoints públicos — no requieren autenticación
    // =========================================================

    @Nested
    @DisplayName("Endpoints públicos — accesibles sin headers de seguridad")
    class EndpointsPublicos {

        @Test
        @DisplayName("GET /actuator/health — health check accesible sin autenticación (pasa security)")
        void actuatorHealth_sinHeaders_noDebeRetornar403() throws Exception {
            // El health check pasa la seguridad (no retorna 403/401).
            // Puede retornar 200 (healthy) o 503 (unhealthy en test), pero NUNCA 403.
            var result = mockMvc.perform(get("/actuator/health"))
                    .andReturn();
            int status = result.getResponse().getStatus();
            assertTrue(status == 200 || status == 503,
                    "Health check debe retornar 200 o 503, no " + status + " (security debe permitirlo)");
        }

        @Test
        @DisplayName("GET /v3/api-docs — documentación OpenAPI accesible sin autenticación")
        void apiDocs_sinHeaders_debeRetornar200() throws Exception {
            // /v3/api-docs puede retornar 200 o redirect, pero NO 401/403
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================
    // Endpoints de usuario — requieren ROLE_USER
    // =========================================================

    @Nested
    @DisplayName("POST /api/lending/prestamos — crear préstamo requiere ROLE_USER")
    class CrearPrestamo {

        @Test
        @DisplayName("Sin X-User-Roles → 403 Forbidden (no llega al controller)")
        void crearPrestamo_sinRoles_debeRetornar403() throws Exception {
            PrestamoRequestDTO request = new PrestamoRequestDTO();
            request.setLibroId(1L);

            mockMvc.perform(post("/api/lending/prestamos")
                            .header("X-User-Id", "usuario1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con rol incorrecto (ROLE_GUEST) → 403 Forbidden")
        void crearPrestamo_conRolIncorrecto_debeRetornar403() throws Exception {
            PrestamoRequestDTO request = new PrestamoRequestDTO();
            request.setLibroId(1L);

            mockMvc.perform(post("/api/lending/prestamos")
                            .header("X-User-Id", "usuario1")
                            .header("X-User-Roles", "ROLE_GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sin ningún header de seguridad → 403 Forbidden")
        void crearPrestamo_sinNingunHeader_debeRetornar403() throws Exception {
            PrestamoRequestDTO request = new PrestamoRequestDTO();
            request.setLibroId(1L);

            mockMvc.perform(post("/api/lending/prestamos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con ROLE_USER y datos válidos → 201 Created (llega al controller)")
        void crearPrestamo_conRoleUser_debeRetornar201() throws Exception {
            PrestamoRequestDTO request = new PrestamoRequestDTO();
            request.setLibroId(1L);

            PrestamoResponseDTO response = crearPrestamoResponse(1L, "usuario1", 1L, EstadoPrestamo.ACTIVO);
            when(prestamoService.crearPrestamo(any(PrestamoRequestDTO.class), any(String.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/lending/prestamos")
                            .header("X-User-Id", "usuario1")
                            .header("X-User-Roles", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Con solo ROLE_ADMIN → 403 Forbidden (hasRole('USER') requiere ROLE_USER explícito)")
        void crearPrestamo_conSoloRoleAdmin_debeRetornar403() throws Exception {
            PrestamoRequestDTO request = new PrestamoRequestDTO();
            request.setLibroId(1L);

            // ADMIN solo no tiene ROLE_USER, por lo tanto hasRole("USER") falla
            mockMvc.perform(post("/api/lending/prestamos")
                            .header("X-User-Id", "admin1")
                            .header("X-User-Roles", "ROLE_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con ROLE_ADMIN y ROLE_USER → 201 Created (ambos roles presentes)")
        void crearPrestamo_conRoleAdminYUser_debeRetornar201() throws Exception {
            PrestamoRequestDTO request = new PrestamoRequestDTO();
            request.setLibroId(1L);

            PrestamoResponseDTO response = crearPrestamoResponse(1L, "admin1", 1L, EstadoPrestamo.ACTIVO);
            when(prestamoService.crearPrestamo(any(PrestamoRequestDTO.class), any(String.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/lending/prestamos")
                            .header("X-User-Id", "admin1")
                            .header("X-User-Roles", "ROLE_ADMIN,ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    // =========================================================
    // GET /api/lending/prestamos/activos — requiere ROLE_USER
    // =========================================================

    @Nested
    @DisplayName("GET /api/lending/prestamos/activos — préstamos propios requiere ROLE_USER")
    class ObtenerActivos {

        @Test
        @DisplayName("Sin X-User-Roles → 403 Forbidden")
        void obtenerActivos_sinRoles_debeRetornar403() throws Exception {
            mockMvc.perform(get("/api/lending/prestamos/activos")
                            .header("X-User-Id", "usuario1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sin ningún header → 403 Forbidden")
        void obtenerActivos_sinHeaders_debeRetornar403() throws Exception {
            mockMvc.perform(get("/api/lending/prestamos/activos"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con ROLE_USER → 200 OK (llega al controller)")
        void obtenerActivos_conRoleUser_debeRetornar200() throws Exception {
            when(prestamoService.obtenerPrestamosActivos("usuario1"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/lending/prestamos/activos")
                            .header("X-User-Id", "usuario1")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================
    // GET /api/lending/prestamos/historial — requiere ROLE_USER
    // =========================================================

    @Nested
    @DisplayName("GET /api/lending/prestamos/historial — historial propio requiere ROLE_USER")
    class ObtenerHistorial {

        @Test
        @DisplayName("Sin X-User-Roles → 403 Forbidden")
        void obtenerHistorial_sinRoles_debeRetornar403() throws Exception {
            mockMvc.perform(get("/api/lending/prestamos/historial")
                            .header("X-User-Id", "usuario1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con ROLE_USER → 200 OK (llega al controller)")
        void obtenerHistorial_conRoleUser_debeRetornar200() throws Exception {
            when(prestamoService.obtenerHistorial("usuario1"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/lending/prestamos/historial")
                            .header("X-User-Id", "usuario1")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================
    // GET /api/lending/prestamos/historial/{usuarioId} — requiere ROLE_USER
    // La validación IDOR se hace en el controller, SecurityConfig solo exige USER
    // =========================================================

    @Nested
    @DisplayName("GET /api/lending/prestamos/historial/{usuarioId} — requiere ROLE_USER + validación IDOR en controller")
    class ObtenerHistorialPorId {

        @Test
        @DisplayName("Sin X-User-Roles → 403 Forbidden (SecurityConfig bloquea antes del controller)")
        void obtenerHistorialPorId_sinRoles_debeRetornar403() throws Exception {
            mockMvc.perform(get("/api/lending/prestamos/historial/usuario1")
                            .header("X-User-Id", "usuario1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con ROLE_USER y mismo usuarioId → 200 OK (pasa SecurityConfig + IDOR)")
        void obtenerHistorialPorId_conRoleUser_mismoUsuario_debeRetornar200() throws Exception {
            when(prestamoService.obtenerHistorial("usuario1"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/lending/prestamos/historial/usuario1")
                            .header("X-User-Id", "usuario1")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Con ROLE_USER y otro usuarioId → 403 (pasa SecurityConfig, pero IDOR bloquea)")
        void obtenerHistorialPorId_conRoleUser_otroUsuario_debeRetornar403() throws Exception {
            mockMvc.perform(get("/api/lending/prestamos/historial/usuario1")
                            .header("X-User-Id", "otro_usuario")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con ROLE_ADMIN y ROLE_USER + otro usuarioId → 200 OK (admin bypass en IDOR)")
        void obtenerHistorialPorId_conRoleAdminYUser_otroUsuario_debeRetornar200() throws Exception {
            when(prestamoService.obtenerHistorial("usuario1"))
                    .thenReturn(Collections.emptyList());

            // ADMIN necesita también ROLE_USER para pasar hasRole("USER") en SecurityConfig.
            // La validación IDOR en el controller permite el acceso porque currentUserRoles contiene ROLE_ADMIN.
            mockMvc.perform(get("/api/lending/prestamos/historial/usuario1")
                            .header("X-User-Id", "admin")
                            .header("X-User-Roles", "ROLE_ADMIN,ROLE_USER"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Con solo ROLE_ADMIN + otro usuarioId → 403 (hasRole('USER') requiere ROLE_USER explícito)")
        void obtenerHistorialPorId_conSoloRoleAdmin_otroUsuario_debeRetornar403() throws Exception {
            // ADMIN solo no tiene ROLE_USER → SecurityConfig bloquea con 403 antes de llegar al controller.
            // Esto es un hallazgo de seguridad: un admin puro no puede acceder al historial de otros usuarios.
            mockMvc.perform(get("/api/lending/prestamos/historial/usuario1")
                            .header("X-User-Id", "admin")
                            .header("X-User-Roles", "ROLE_ADMIN"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================
    // GET /api/lending/prestamos/todos — requiere ROLE_ADMIN
    // =========================================================

    @Nested
    @DisplayName("GET /api/lending/prestamos/todos — requiere ROLE_ADMIN (uso interno Analytics)")
    class ObtenerTodos {

        @Test
        @DisplayName("Sin headers → 403 Forbidden")
        void obtenerTodos_sinHeaders_debeRetornar403() throws Exception {
            mockMvc.perform(get("/api/lending/prestamos/todos"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con ROLE_USER → 403 Forbidden (solo ADMIN puede acceder)")
        void obtenerTodos_conRoleUser_debeRetornar403() throws Exception {
            mockMvc.perform(get("/api/lending/prestamos/todos")
                            .header("X-User-Id", "usuario1")
                            .header("X-User-Roles", "ROLE_USER"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con rol incorrecto (ROLE_GUEST) → 403 Forbidden")
        void obtenerTodos_conRolIncorrecto_debeRetornar403() throws Exception {
            mockMvc.perform(get("/api/lending/prestamos/todos")
                            .header("X-User-Id", "guest")
                            .header("X-User-Roles", "ROLE_GUEST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con ROLE_ADMIN → 200 OK (llega al controller)")
        void obtenerTodos_conRoleAdmin_debeRetornar200() throws Exception {
            Page<PrestamoResponseDTO> page = new PageImpl<>(Collections.emptyList());
            when(prestamoService.obtenerTodos(any())).thenReturn(page);

            mockMvc.perform(get("/api/lending/prestamos/todos")
                            .header("X-User-Id", "admin")
                            .header("X-User-Roles", "ROLE_ADMIN"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Con ROLE_ADMIN y ROLE_USER → 200 OK (múltiples roles)")
        void obtenerTodos_conMultiplesRoles_debeRetornar200() throws Exception {
            Page<PrestamoResponseDTO> page = new PageImpl<>(Collections.emptyList());
            when(prestamoService.obtenerTodos(any())).thenReturn(page);

            mockMvc.perform(get("/api/lending/prestamos/todos")
                            .header("X-User-Id", "admin")
                            .header("X-User-Roles", "ROLE_ADMIN,ROLE_USER"))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================
    // Fallback — anyRequest().authenticated()
    // =========================================================

    @Nested
    @DisplayName("Fallback — anyRequest().authenticated() se satisface porque el filtro siempre setea Authentication")
    class Fallback {

        @Test
        @DisplayName("GET /api/lending/prestamos/inexistente sin roles → 404 (filtro siempre setea auth, endpoint no existe)")
        void endpointInexistente_sinRoles_debeRetornar404() throws Exception {
            // El JwtAuthenticationFilter SIEMPRE setea Authentication (con valores nulos si no hay headers).
            // anyRequest().authenticated() se satisface porque hay un objeto Authentication.
            // Spring MVC retorna 404 porque la URL no coincide con ningún controller.
            mockMvc.perform(get("/api/lending/prestamos/inexistente"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/lending/prestamos/1 sin roles → 404 (filtro siempre setea auth, endpoint no existe)")
        void metodoNoListado_sinRoles_debeRetornar404() throws Exception {
            // Mismo razonamiento: la seguridad se satisface, pero no hay mapping para esta URL.
            mockMvc.perform(get("/api/lending/prestamos/1"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================
    // Helper
    // =========================================================

    private PrestamoResponseDTO crearPrestamoResponse(
            Long id, String usuarioId, Long libroId, EstadoPrestamo estado) {
        PrestamoResponseDTO dto = new PrestamoResponseDTO();
        dto.setId(id);
        dto.setUsuarioId(usuarioId);
        dto.setLibroId(libroId);
        dto.setFechaInicio(LocalDateTime.now());
        dto.setFechaVencimiento(LocalDateTime.now().plusDays(7));
        dto.setEstado(estado);
        return dto;
    }
}
