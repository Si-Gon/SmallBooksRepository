package com.silvio.elending.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.exception.LimitePrestamosExcedidoException;
import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import com.silvio.elending.service.PrestamoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del PrestamoController.
 *
 * Puntos clave de este controller:
 * 1. El usuarioId NO viene en el body — viene del header X-User-Id propagado por el Gateway.
 * 2. Todos los endpoints autenticados requieren el header "X-User-Id".
 */
@WebMvcTest(PrestamoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PrestamoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrestamoService prestamoService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USUARIO_ID = "usuario1";

    // =========================================================
    // POST /api/lending/prestamos  — crear préstamo
    // =========================================================

    @Test
    void crearPrestamo_exitoso_debeRetornar201() throws Exception {
        PrestamoRequestDTO request = new PrestamoRequestDTO();
        request.setLibroId(1L);

        PrestamoResponseDTO response = crearPrestamoResponse(1L, USUARIO_ID, 1L, EstadoPrestamo.ACTIVO);
        when(prestamoService.crearPrestamo(any(PrestamoRequestDTO.class), eq(USUARIO_ID)))
                .thenReturn(response);

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("X-User-Id", USUARIO_ID)
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
        PrestamoRequestDTO requestInvalido = new PrestamoRequestDTO();
        // libroId = null → viola @NotNull

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearPrestamo_libroIdCero_debeRetornar400() throws Exception {
        // @Positive rechaza libroId = 0
        PrestamoRequestDTO requestInvalido = new PrestamoRequestDTO();
        requestInvalido.setLibroId(0L); // viola @Positive

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(prestamoService, never()).crearPrestamo(any(), anyString());
    }

    @Test
    void crearPrestamo_limiteAlcanzado_debeRetornar4xx() throws Exception {
        when(prestamoService.crearPrestamo(any(PrestamoRequestDTO.class), eq(USUARIO_ID)))
                .thenThrow(new LimitePrestamosExcedidoException(2, "BASICO"));

        PrestamoRequestDTO request = new PrestamoRequestDTO();
        request.setLibroId(1L);

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // El GlobalExceptionHandler convierte RuntimeException → 4xx
                .andExpect(status().is4xxClientError());
    }

    @Test
    void crearPrestamo_headerXUserIdAusente_debeRetornar400() throws Exception {
        // Sin header X-User-Id — Spring lanza MissingRequestHeaderException
        PrestamoRequestDTO request = new PrestamoRequestDTO();
        request.setLibroId(1L);

        mockMvc.perform(post("/api/lending/prestamos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(prestamoService, never()).crearPrestamo(any(), anyString());
    }

    // =========================================================
    // GET /api/lending/prestamos/activos  — préstamos activos del usuario
    // =========================================================

    @Test
    void obtenerActivos_debeRetornar200ConLista() throws Exception {
        List<PrestamoResponseDTO> prestamos = Arrays.asList(
                crearPrestamoResponse(1L, USUARIO_ID, 1L, EstadoPrestamo.ACTIVO),
                crearPrestamoResponse(2L, USUARIO_ID, 2L, EstadoPrestamo.ACTIVO)
        );
        when(prestamoService.obtenerPrestamosActivos(USUARIO_ID)).thenReturn(prestamos);

        mockMvc.perform(get("/api/lending/prestamos/activos")
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[1].estado").value("ACTIVO"));

        verify(prestamoService).obtenerPrestamosActivos(USUARIO_ID);
    }

    @Test
    void obtenerActivos_headerXUserIdAusente_debeRetornar400() throws Exception {
        mockMvc.perform(get("/api/lending/prestamos/activos"))
                .andExpect(status().isBadRequest());

        verify(prestamoService, never()).obtenerPrestamosActivos(anyString());
    }

    @Test
    void obtenerActivos_sinPrestamos_debeRetornar200ListaVacia() throws Exception {
        when(prestamoService.obtenerPrestamosActivos(USUARIO_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/lending/prestamos/activos")
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // =========================================================
    // GET /api/lending/prestamos/historial  — historial del usuario
    // =========================================================

    @Test
    void obtenerHistorial_debeRetornar200ConHistorial() throws Exception {
        List<PrestamoResponseDTO> historial = Arrays.asList(
                crearPrestamoResponse(1L, USUARIO_ID, 1L, EstadoPrestamo.ACTIVO),
                crearPrestamoResponse(2L, USUARIO_ID, 2L, EstadoPrestamo.VENCIDO)
        );
        when(prestamoService.obtenerHistorial(USUARIO_ID)).thenReturn(historial);

        mockMvc.perform(get("/api/lending/prestamos/historial")
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[1].estado").value("VENCIDO"));
    }

    @Test
    void obtenerHistorial_headerXUserIdAusente_debeRetornar400() throws Exception {
        mockMvc.perform(get("/api/lending/prestamos/historial"))
                .andExpect(status().isBadRequest());

        verify(prestamoService, never()).obtenerHistorial(anyString());
    }

    @Test
    void crearPrestamo_libroIdNegativo_debeRetornar400() throws Exception {
        // @Positive rechaza libroId negativo
        PrestamoRequestDTO requestInvalido = new PrestamoRequestDTO();
        requestInvalido.setLibroId(-1L); // viola @Positive

        mockMvc.perform(post("/api/lending/prestamos")
                        .header("X-User-Id", USUARIO_ID)
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
        List<PrestamoResponseDTO> content = Arrays.asList(
                crearPrestamoResponse(1L, "usuario1", 1L, EstadoPrestamo.ACTIVO),
                crearPrestamoResponse(2L, "usuario2", 3L, EstadoPrestamo.VENCIDO)
        );
        Page<PrestamoResponseDTO> page = new PageImpl<>(content);
        when(prestamoService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // Este endpoint NO requiere header X-User-Id (es interno)
        mockMvc.perform(get("/api/lending/prestamos/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].usuarioId").value("usuario1"))
                .andExpect(jsonPath("$.content[1].usuarioId").value("usuario2"))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(prestamoService).obtenerTodos(any(Pageable.class));
    }

    @Test
    void obtenerTodos_conSizePersonalizado_debeRetornar200() throws Exception {
        // Given — 3 préstamos en una página de size=10
        List<PrestamoResponseDTO> content = Arrays.asList(
                crearPrestamoResponse(1L, "u1", 1L, EstadoPrestamo.ACTIVO),
                crearPrestamoResponse(2L, "u2", 2L, EstadoPrestamo.VENCIDO),
                crearPrestamoResponse(3L, "u3", 3L, EstadoPrestamo.ACTIVO)
        );
        Page<PrestamoResponseDTO> page = new PageImpl<>(content, PageRequest.of(0, 10), 3);
        when(prestamoService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // When — size=10 personalizado
        mockMvc.perform(get("/api/lending/prestamos/todos")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.size").value(10));

        verify(prestamoService).obtenerTodos(any(Pageable.class));
    }

    @Test
    void obtenerTodos_conPageYSort_debeRetornar200() throws Exception {
        // Given — segunda página con 1 elemento, ordenado por id asc
        List<PrestamoResponseDTO> content = Collections.singletonList(
                crearPrestamoResponse(11L, "u11", 11L, EstadoPrestamo.ACTIVO)
        );
        Page<PrestamoResponseDTO> page = new PageImpl<>(content, PageRequest.of(1, 10, Sort.by("id").ascending()), 11);
        when(prestamoService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // When — page=1, size=10, sort=id,asc
        mockMvc.perform(get("/api/lending/prestamos/todos")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].usuarioId").value("u11"))
                .andExpect(jsonPath("$.content[0].id").value(11))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(prestamoService).obtenerTodos(any(Pageable.class));
    }

    @Test
    void obtenerTodos_conPageInvalido_defaultPageCero() throws Exception {
        // Given — Spring Data trata page negativa como 0 por defecto
        List<PrestamoResponseDTO> content = Collections.singletonList(
                crearPrestamoResponse(5L, "u5", 5L, EstadoPrestamo.ACTIVO)
        );
        Page<PrestamoResponseDTO> page = new PageImpl<>(content, PageRequest.of(0, 50), 1);
        when(prestamoService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // When — page=-1 (invalido, Spring lo corrige a 0)
        mockMvc.perform(get("/api/lending/prestamos/todos")
                        .param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0));

        verify(prestamoService).obtenerTodos(any(Pageable.class));
    }

    @Test
    void obtenerTodos_variosSorts_debeAceptarlos() throws Exception {
        // Given — sort por fechaInicio desc y estado asc
        List<PrestamoResponseDTO> content = Arrays.asList(
                crearPrestamoResponse(1L, "u1", 1L, EstadoPrestamo.ACTIVO),
                crearPrestamoResponse(2L, "u2", 2L, EstadoPrestamo.VENCIDO)
        );
        Page<PrestamoResponseDTO> page = new PageImpl<>(content, PageRequest.of(0, 50, Sort.by(
                Sort.Order.desc("fechaInicio"), Sort.Order.asc("estado"))), 2);
        when(prestamoService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // When — sort=fechaInicio,desc&sort=estado,asc
        mockMvc.perform(get("/api/lending/prestamos/todos")
                        .param("sort", "fechaInicio,desc")
                        .param("sort", "estado,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(prestamoService).obtenerTodos(any(Pageable.class));
    }

    @Test
    void obtenerTodos_conPageableCaptor_verificaDefaults() throws Exception {
        // Given — page vacia para verificar que los parametros default se pasan correctamente
        Page<PrestamoResponseDTO> page = Page.empty();
        when(prestamoService.obtenerTodos(any(Pageable.class))).thenReturn(page);

        // When — sin parametros
        mockMvc.perform(get("/api/lending/prestamos/todos"))
                .andExpect(status().isOk());

        // Then — el Pageable debe tener size=50 y sort=fechaInicio,DESC
        verify(prestamoService).obtenerTodos(argThat(p -> {
            Pageable pageable = (Pageable) p;
            Sort sort = pageable.getSort();
            boolean sortCorrecto = sort.getOrderFor("fechaInicio") != null
                    && sort.getOrderFor("fechaInicio").getDirection().equals(Sort.Direction.DESC);
            boolean sizeCorrecto = pageable.getPageSize() == 50;
            return sortCorrecto && sizeCorrecto;
        }));
    }

    // =========================================================
    // GET /api/lending/prestamos/historial/{usuarioId}  — por ID (Analytics)
    // =========================================================

    @Test
    void obtenerHistorialPorId_debeRetornar200() throws Exception {
        // Given
        List<PrestamoResponseDTO> historial = Arrays.asList(
                crearPrestamoResponse(1L, "usuario1", 5L, EstadoPrestamo.VENCIDO)
        );
        when(prestamoService.obtenerHistorial("usuario1")).thenReturn(historial);

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(get("/api/lending/prestamos/historial/usuario1")
                        .header("X-User-Id", "usuario1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioId").value("usuario1"));
    }

    @Test
    void obtenerHistorialPorId_listaVacia_debeRetornar200() throws Exception {
        // Given — el servicio retorna lista vacía (sin excepción) — debe dar 200, no 404
        when(prestamoService.obtenerHistorial("usuario_sin_prestamos")).thenReturn(List.of());

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(get("/api/lending/prestamos/historial/usuario_sin_prestamos")
                        .header("X-User-Id", "usuario_sin_prestamos")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(prestamoService).obtenerHistorial("usuario_sin_prestamos");
    }

    // =========================================================
    // IDOR: validación de acceso a datos de otro usuario
    // =========================================================

    @Test
    void obtenerHistorialPorId_cuando_mismoUsuario_devuelve200() throws Exception {
        // Given
        List<PrestamoResponseDTO> historial = Arrays.asList(
                crearPrestamoResponse(1L, "usuario1", 5L, EstadoPrestamo.VENCIDO)
        );
        when(prestamoService.obtenerHistorial("usuario1")).thenReturn(historial);

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(get("/api/lending/prestamos/historial/usuario1")
                        .header("X-User-Id", "usuario1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk());

        verify(prestamoService).obtenerHistorial("usuario1");
    }

    @Test
    void obtenerHistorialPorId_cuando_otroUsuario_devuelve403() throws Exception {
        // Given — X-User-Id ("otro") NO coincide con {usuarioId} ("usuario1")

        // When & Then
        mockMvc.perform(get("/api/lending/prestamos/historial/usuario1")
                        .header("X-User-Id", "otro")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(prestamoService, never()).obtenerHistorial(anyString());
    }

    @Test
    void obtenerHistorialPorId_cuando_admin_devuelve200() throws Exception {
        // Given
        List<PrestamoResponseDTO> historial = Arrays.asList(
                crearPrestamoResponse(1L, "usuario1", 5L, EstadoPrestamo.VENCIDO)
        );
        when(prestamoService.obtenerHistorial("usuario1")).thenReturn(historial);

        // When & Then — ROLE_ADMIN puede acceder a cualquier usuarioId
        mockMvc.perform(get("/api/lending/prestamos/historial/usuario1")
                        .header("X-User-Id", "admin")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());

        verify(prestamoService).obtenerHistorial("usuario1");
    }

    @Test
    void obtenerHistorialPorId_cuando_headerAusente_devuelve403() throws Exception {
        // Given — sin header X-User-Id

        // When & Then
        mockMvc.perform(get("/api/lending/prestamos/historial/usuario1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(prestamoService, never()).obtenerHistorial(anyString());
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
