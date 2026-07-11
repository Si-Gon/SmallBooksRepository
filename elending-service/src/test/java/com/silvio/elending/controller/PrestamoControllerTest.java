package com.silvio.elending.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import com.silvio.elending.security.JwtExtractor;
import com.silvio.elending.service.PrestamoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del PrestamoController.
 *
 * Puntos clave de este controller:
 * 1. El usuarioId NO viene en el body — viene del token JWT via JwtExtractor.
 * 2. Todos los endpoints autenticados requieren el header "Authorization".
 * 3. JwtExtractor también se mockea — no queremos validar tokens JWT reales en tests.
 */
@WebMvcTest(PrestamoController.class)
class PrestamoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrestamoService prestamoService;

    // MockBean del JwtExtractor — necesario porque el Controller lo inyecta con @RequiredArgsConstructor
    @MockBean
    private JwtExtractor jwtExtractor;

    @Autowired
    private ObjectMapper objectMapper;

    // Token JWT ficticio — no necesita ser válido porque JwtExtractor está mockeado
    private static final String FAKE_JWT = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3VhcmlvMSJ9.fake";
    private static final String USUARIO_ID = "usuario1";

    // =========================================================
    // POST /api/lending/prestamos  — crear préstamo
    // =========================================================

    @Test
    void crearPrestamo_exitoso_debeRetornar201() throws Exception {
        // El mock del JwtExtractor devuelve el usuarioId sin validar el token real
        when(jwtExtractor.extraerUsuario(FAKE_JWT)).thenReturn(USUARIO_ID);

        PrestamoRequestDTO request = new PrestamoRequestDTO();
        request.setLibroId(1L);

        PrestamoResponseDTO response = crearPrestamoResponse(1L, USUARIO_ID, 1L, EstadoPrestamo.ACTIVO);
        when(prestamoService.crearPrestamo(any(PrestamoRequestDTO.class), eq(USUARIO_ID)))
                .thenReturn(response);

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("Authorization", FAKE_JWT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(USUARIO_ID))
                .andExpect(jsonPath("$.libroId").value(1))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        verify(prestamoService).crearPrestamo(any(PrestamoRequestDTO.class), eq(USUARIO_ID));
    }

    @Test
    void crearPrestamo_sinLibroId_debeRetornar400() throws Exception {
        // @NotNull en libroId debe rechazar el body con campo ausente
        when(jwtExtractor.extraerUsuario(FAKE_JWT)).thenReturn(USUARIO_ID);

        PrestamoRequestDTO requestInvalido = new PrestamoRequestDTO();
        // libroId = null → viola @NotNull

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("Authorization", FAKE_JWT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearPrestamo_libroIdCero_debeRetornar400() throws Exception {
        // @Positive rechaza libroId = 0
        when(jwtExtractor.extraerUsuario(FAKE_JWT)).thenReturn(USUARIO_ID);

        PrestamoRequestDTO requestInvalido = new PrestamoRequestDTO();
        requestInvalido.setLibroId(0L); // viola @Positive

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("Authorization", FAKE_JWT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(prestamoService, never()).crearPrestamo(any(), anyString());
    }

    @Test
    void crearPrestamo_limiteAlcanzado_debeRetornar4xx() throws Exception {
        when(jwtExtractor.extraerUsuario(FAKE_JWT)).thenReturn(USUARIO_ID);
        when(prestamoService.crearPrestamo(any(PrestamoRequestDTO.class), eq(USUARIO_ID)))
                .thenThrow(new RuntimeException("Has alcanzado el límite de 2 préstamos activos para tu plan BASICO"));

        PrestamoRequestDTO request = new PrestamoRequestDTO();
        request.setLibroId(1L);

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("Authorization", FAKE_JWT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // El GlobalExceptionHandler convierte RuntimeException → 4xx
                .andExpect(status().is4xxClientError());
    }

    @Test
    void crearPrestamo_tokenInvalido_debeRetornar4xx() throws Exception {
        // JwtExtractor lanza excepción cuando el token no puede parsearse
        when(jwtExtractor.extraerUsuario(any())).thenThrow(
                new RuntimeException("No se pudo extraer el usuario del token"));

        PrestamoRequestDTO request = new PrestamoRequestDTO();
        request.setLibroId(1L);

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("Authorization", "Bearer token_invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================
    // GET /api/lending/prestamos/activos  — préstamos activos del usuario
    // =========================================================

    @Test
    void obtenerActivos_debeRetornar200ConLista() throws Exception {
        when(jwtExtractor.extraerUsuario(FAKE_JWT)).thenReturn(USUARIO_ID);

        List<PrestamoResponseDTO> prestamos = Arrays.asList(
                crearPrestamoResponse(1L, USUARIO_ID, 1L, EstadoPrestamo.ACTIVO),
                crearPrestamoResponse(2L, USUARIO_ID, 2L, EstadoPrestamo.ACTIVO)
        );
        when(prestamoService.obtenerPrestamosActivos(USUARIO_ID)).thenReturn(prestamos);

        mockMvc.perform(get("/api/lending/prestamos/activos")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[1].estado").value("ACTIVO"));

        verify(prestamoService).obtenerPrestamosActivos(USUARIO_ID);
    }

    @Test
    void obtenerActivos_tokenInvalido_debeRetornar4xx() throws Exception {
        // JwtExtractor lanza excepción con token inválido
        when(jwtExtractor.extraerUsuario(any())).thenThrow(
                new RuntimeException("No se pudo extraer el usuario del token"));

        mockMvc.perform(get("/api/lending/prestamos/activos")
                        .header("Authorization", "Bearer token_invalido"))
                .andExpect(status().is4xxClientError());

        verify(prestamoService, never()).obtenerPrestamosActivos(anyString());
    }

    @Test
    void obtenerActivos_sinPrestamos_debeRetornar200ListaVacia() throws Exception {
        when(jwtExtractor.extraerUsuario(FAKE_JWT)).thenReturn(USUARIO_ID);
        when(prestamoService.obtenerPrestamosActivos(USUARIO_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/lending/prestamos/activos")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // =========================================================
    // GET /api/lending/prestamos/historial  — historial del usuario
    // =========================================================

    @Test
    void obtenerHistorial_debeRetornar200ConHistorial() throws Exception {
        when(jwtExtractor.extraerUsuario(FAKE_JWT)).thenReturn(USUARIO_ID);

        List<PrestamoResponseDTO> historial = Arrays.asList(
                crearPrestamoResponse(1L, USUARIO_ID, 1L, EstadoPrestamo.ACTIVO),
                crearPrestamoResponse(2L, USUARIO_ID, 2L, EstadoPrestamo.VENCIDO)
        );
        when(prestamoService.obtenerHistorial(USUARIO_ID)).thenReturn(historial);

        mockMvc.perform(get("/api/lending/prestamos/historial")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[1].estado").value("VENCIDO"));
    }

    @Test
    void obtenerHistorial_tokenInvalido_debeRetornar4xx() throws Exception {
        when(jwtExtractor.extraerUsuario(any())).thenThrow(
                new RuntimeException("No se pudo extraer el usuario del token"));

        mockMvc.perform(get("/api/lending/prestamos/historial")
                        .header("Authorization", "Bearer token_invalido"))
                .andExpect(status().is4xxClientError());

        verify(prestamoService, never()).obtenerHistorial(anyString());
    }

    @Test
    void crearPrestamo_sinToken_debeRetornar401() throws Exception {
        // Sin header Authorization — el controller falla antes de llegar al service
        PrestamoRequestDTO request = new PrestamoRequestDTO();
        request.setLibroId(1L);

        mockMvc.perform(post("/api/lending/prestamos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // Missing header → error de servlet
                // Nota: @RequestHeader sin required=false → error de servlet, no entra al handler

        verify(prestamoService, never()).crearPrestamo(any(), anyString());
    }

    @Test
    void crearPrestamo_libroIdNegativo_debeRetornar400() throws Exception {
        // @Positive rechaza libroId negativo
        when(jwtExtractor.extraerUsuario(FAKE_JWT)).thenReturn(USUARIO_ID);

        PrestamoRequestDTO requestInvalido = new PrestamoRequestDTO();
        requestInvalido.setLibroId(-1L); // viola @Positive

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("Authorization", FAKE_JWT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(prestamoService, never()).crearPrestamo(any(), anyString());
    }

    // =========================================================
    // GET /api/lending/prestamos/todos  — todos los préstamos (Analytics)
    // =========================================================

    @Test
    void obtenerTodos_debeRetornar200() throws Exception {
        List<PrestamoResponseDTO> todos = Arrays.asList(
                crearPrestamoResponse(1L, "usuario1", 1L, EstadoPrestamo.ACTIVO),
                crearPrestamoResponse(2L, "usuario2", 3L, EstadoPrestamo.VENCIDO)
        );
        when(prestamoService.obtenerTodos()).thenReturn(todos);

        // Este endpoint NO requiere header Authorization (es interno)
        mockMvc.perform(get("/api/lending/prestamos/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioId").value("usuario1"))
                .andExpect(jsonPath("$[1].usuarioId").value("usuario2"));

        verify(prestamoService).obtenerTodos();
    }

    // =========================================================
    // GET /api/lending/prestamos/historial/{usuarioId}  — por ID (Analytics)
    // =========================================================

    @Test
    void obtenerHistorialPorId_debeRetornar200() throws Exception {
        List<PrestamoResponseDTO> historial = Arrays.asList(
                crearPrestamoResponse(1L, "usuario1", 5L, EstadoPrestamo.VENCIDO)
        );
        when(prestamoService.obtenerHistorial("usuario1")).thenReturn(historial);

        mockMvc.perform(get("/api/lending/prestamos/historial/usuario1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioId").value("usuario1"));
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
