package com.silvio.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.exception.NotificacionNotFoundException;
import com.silvio.notification.model.Notificacion.TipoNotificacion;
import com.silvio.notification.service.NotificacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificacionController.class)
class NotificacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificacionService notificacionService;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private NotificacionDTO notificacionDTO(Long id, String usuarioId,
                                            TipoNotificacion tipo, boolean leida) {
        NotificacionDTO dto = new NotificacionDTO();
        dto.setId(id);
        dto.setUsuarioId(usuarioId);
        dto.setTipo(tipo);
        dto.setMensaje("Mensaje de prueba");
        dto.setFechaEnvio(LocalDateTime.now());
        dto.setLeida(leida);
        return dto;
    }

    private NotificacionRequestDTO notificacionRequest(String usuarioId,
                                                        TipoNotificacion tipo) {
        NotificacionRequestDTO req = new NotificacionRequestDTO();
        req.setUsuarioId(usuarioId);
        req.setTipo(tipo);
        req.setMensaje("Mensaje de prueba");
        return req;
    }

    // ─── POST /api/notifications ──────────────────────────────────────────────

    @Test
    void crear_devuelve_201_con_datos_validos() throws Exception {
        // Given
        NotificacionRequestDTO request = notificacionRequest(
                "usuario1", TipoNotificacion.PRESTAMO_CREADO);
        NotificacionDTO response = notificacionDTO(
                1L, "usuario1", TipoNotificacion.PRESTAMO_CREADO, false);
        when(notificacionService.crear(any(NotificacionRequestDTO.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value("usuario1"))
                .andExpect(jsonPath("$.tipo").value("PRESTAMO_CREADO"))
                .andExpect(jsonPath("$.leida").value(false))
                .andExpect(jsonPath("$._links.mis-notificaciones").exists())
                .andExpect(jsonPath("$._links.no-leidas").exists())
                .andExpect(jsonPath("$._links.marcar-leida").exists());

        verify(notificacionService).crear(any(NotificacionRequestDTO.class));
    }

    @Test
    void crear_devuelve_201_tipo_PRESTAMO_VENCIDO() throws Exception {
        // Given
        NotificacionRequestDTO request = notificacionRequest(
                "usuario1", TipoNotificacion.VENCIDO);
        NotificacionDTO response = notificacionDTO(
                2L, "usuario1", TipoNotificacion.VENCIDO, false);
        when(notificacionService.crear(any(NotificacionRequestDTO.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("VENCIDO"))
                .andExpect(jsonPath("$.leida").value(false));

        verify(notificacionService).crear(any(NotificacionRequestDTO.class));
    }

    @Test
    void crear_devuelve_400_cuando_usuarioId_es_null() throws Exception {
        // Given — viola @NotNull si existe en NotificacionRequestDTO
        NotificacionRequestDTO request = new NotificacionRequestDTO();
        request.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        request.setMensaje("Mensaje");
        // usuarioId queda null

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(notificacionService, never()).crear(any());
    }

    @Test
    void crear_devuelve_201_con_mismo_id_para_mismo_contenido() throws Exception {
        // Given — mismo body dos veces (idempotencia en service)
        NotificacionRequestDTO request = notificacionRequest(
                "usuario1", TipoNotificacion.PRESTAMO_CREADO);
        NotificacionDTO response = notificacionDTO(
                1L, "usuario1", TipoNotificacion.PRESTAMO_CREADO, false);

        // El service devuelve el mismo DTO (existente) para el duplicado
        when(notificacionService.crear(any(NotificacionRequestDTO.class)))
                .thenReturn(response);

        // When — primera llamada
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$._links.mis-notificaciones").exists());

        // Then — segunda llamada con mismo body también retorna 201 y mismo id
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$._links.mis-notificaciones").exists());

        verify(notificacionService, times(2)).crear(any(NotificacionRequestDTO.class));
    }

    // ─── GET /api/notifications/usuario/{usuarioId} ───────────────────────────

    @Test
    void obtenerPorUsuario_devuelve_200_con_lista() throws Exception {
        // Given
        List<NotificacionDTO> lista = Arrays.asList(
                notificacionDTO(1L, "usuario1", TipoNotificacion.PRESTAMO_CREADO, false),
                notificacionDTO(2L, "usuario1", TipoNotificacion.VENCIDO, true)
        );
        when(notificacionService.obtenerPorUsuario("usuario1")).thenReturn(lista);

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(get("/api/notifications/usuario/usuario1")
                        .header("X-User-Id", "usuario1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].leida").value(false))
                .andExpect(jsonPath("$[1].leida").value(true));

        verify(notificacionService).obtenerPorUsuario("usuario1");
    }

    @Test
    void obtenerPorUsuario_devuelve_200_con_lista_vacia() throws Exception {
        // Given
        when(notificacionService.obtenerPorUsuario("usuario_sin_notifs"))
                .thenReturn(List.of());

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(get("/api/notifications/usuario/usuario_sin_notifs")
                        .header("X-User-Id", "usuario_sin_notifs")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(notificacionService).obtenerPorUsuario("usuario_sin_notifs");
    }

    // ─── GET /api/notifications/usuario/{usuarioId}/no-leidas ─────────────────

    @Test
    void obtenerNoLeidas_devuelve_200_solo_no_leidas() throws Exception {
        // Given
        List<NotificacionDTO> noLeidas = Arrays.asList(
                notificacionDTO(1L, "usuario1", TipoNotificacion.PRESTAMO_CREADO, false),
                notificacionDTO(3L, "usuario1", TipoNotificacion.PROXIMO_VENCER, false)
        );
        when(notificacionService.obtenerNoLeidas("usuario1")).thenReturn(noLeidas);

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(get("/api/notifications/usuario/usuario1/no-leidas")
                        .header("X-User-Id", "usuario1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].leida").value(false))
                .andExpect(jsonPath("$[1].leida").value(false));
                

        verify(notificacionService).obtenerNoLeidas("usuario1");
    }

    @Test
    void obtenerNoLeidas_devuelve_200_lista_vacia_si_todas_leidas() throws Exception {
        // Given
        when(notificacionService.obtenerNoLeidas("usuario1")).thenReturn(List.of());

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(get("/api/notifications/usuario/usuario1/no-leidas")
                        .header("X-User-Id", "usuario1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── @Positive validation ────────────────────────────────────────────────

    @Test
    void marcarLeida_conIdNegativo_debeRetornar400() throws Exception {
        mockMvc.perform(patch("/api/notifications/-1/leer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.id").value("El ID debe ser un número positivo"))
                .andExpect(jsonPath("$.codigo").value("ERR-400"));

        verify(notificacionService, never()).marcarLeida(anyLong());
    }

    // ─── PATCH /api/notifications/{id}/leer ──────────────────────────────────

    @Test
    void marcarLeida_devuelve_200_cuando_notificacion_existe() throws Exception {
        // Given
        NotificacionDTO leida = notificacionDTO(
                1L, "usuario1", TipoNotificacion.PRESTAMO_CREADO, true);
        when(notificacionService.marcarLeida(1L)).thenReturn(leida);

        // When & Then
        mockMvc.perform(patch("/api/notifications/1/leer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true))
                .andExpect(jsonPath("$._links.mis-notificaciones").exists())
                .andExpect(jsonPath("$._links.marcar-todas-leidas").exists());

        verify(notificacionService).marcarLeida(1L);
    }

    @Test
    void marcarLeida_devuelve_404_cuando_notificacion_no_existe() throws Exception {
        // Given
        when(notificacionService.marcarLeida(999L))
                .thenThrow(new NotificacionNotFoundException(999L));

        // When & Then
        mockMvc.perform(patch("/api/notifications/999/leer"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        verify(notificacionService).marcarLeida(999L);
    }

    // ─── PATCH /api/notifications/usuario/{usuarioId}/leer-todas ─────────────

   @Test
    void marcarTodasLeidas_devuelve_204() throws Exception {
        // Given
        doNothing().when(notificacionService).marcarTodasLeidas("usuario1");

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(patch("/api/notifications/usuario/usuario1/leer-todas")
                        .header("X-User-Id", "usuario1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isNoContent());

        verify(notificacionService).marcarTodasLeidas("usuario1");
    }

    // ─── Error interno (RuntimeException) → 500 ─────────────────────────────

    @Test
    void crear_errorInterno_debeRetornar500() throws Exception {
        // Given
        NotificacionRequestDTO request = notificacionRequest(
                "usuario1", TipoNotificacion.PRESTAMO_CREADO);
        when(notificacionService.crear(any(NotificacionRequestDTO.class)))
                .thenThrow(new RuntimeException("Error inesperado de base de datos"));

        // When & Then
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void obtenerPorUsuario_errorInterno_debeRetornar500() throws Exception {
        // Given
        when(notificacionService.obtenerPorUsuario("error500"))
                .thenThrow(new RuntimeException("Error inesperado de base de datos"));

        // When & Then
        mockMvc.perform(get("/api/notifications/usuario/error500")
                        .header("X-User-Id", "error500")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void obtenerNoLeidas_errorInterno_debeRetornar500() throws Exception {
        // Given
        when(notificacionService.obtenerNoLeidas("error500"))
                .thenThrow(new RuntimeException("Error inesperado de base de datos"));

        // When & Then
        mockMvc.perform(get("/api/notifications/usuario/error500/no-leidas")
                        .header("X-User-Id", "error500")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void marcarLeida_errorInterno_debeRetornar500() throws Exception {
        // Given
        when(notificacionService.marcarLeida(1L))
                .thenThrow(new RuntimeException("Error inesperado de base de datos"));

        // When & Then
        mockMvc.perform(patch("/api/notifications/1/leer"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    // ══════════════════════════════════════════════════════════════════════
    // IDOR: validación de acceso a datos de otro usuario
    // ══════════════════════════════════════════════════════════════════════

    // ─── GET /api/notifications/usuario/{usuarioId} ───────────────────────

    @Test
    void obtenerPorUsuario_cuando_mismoUsuario_devuelve200() throws Exception {
        // Given
        List<NotificacionDTO> lista = Arrays.asList(
                notificacionDTO(1L, "usuario1", TipoNotificacion.PRESTAMO_CREADO, false)
        );
        when(notificacionService.obtenerPorUsuario("usuario1")).thenReturn(lista);

        // When & Then — X-User-Id coincide con {usuarioId}
        mockMvc.perform(get("/api/notifications/usuario/usuario1")
                        .header("X-User-Id", "usuario1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk());

        verify(notificacionService).obtenerPorUsuario("usuario1");
    }

    @Test
    void obtenerPorUsuario_cuando_otroUsuario_devuelve403() throws Exception {
        // Given — X-User-Id ("otro") NO coincide con {usuarioId} ("usuario1")

        // When & Then
        mockMvc.perform(get("/api/notifications/usuario/usuario1")
                        .header("X-User-Id", "otro")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(notificacionService, never()).obtenerPorUsuario(anyString());
    }

    @Test
    void obtenerPorUsuario_cuando_admin_devuelve200() throws Exception {
        // Given
        List<NotificacionDTO> lista = Arrays.asList(
                notificacionDTO(1L, "usuario1", TipoNotificacion.PRESTAMO_CREADO, false)
        );
        when(notificacionService.obtenerPorUsuario("usuario1")).thenReturn(lista);

        // When & Then — ROLE_ADMIN puede acceder a cualquier usuarioId
        mockMvc.perform(get("/api/notifications/usuario/usuario1")
                        .header("X-User-Id", "admin")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());

        verify(notificacionService).obtenerPorUsuario("usuario1");
    }

    @Test
    void obtenerPorUsuario_cuando_headerAusente_devuelve403() throws Exception {
        // Given — sin header X-User-Id

        // When & Then
        mockMvc.perform(get("/api/notifications/usuario/usuario1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(notificacionService, never()).obtenerPorUsuario(anyString());
    }

    // ─── GET /api/notifications/usuario/{usuarioId}/no-leidas ─────────────

    @Test
    void obtenerNoLeidas_cuando_mismoUsuario_devuelve200() throws Exception {
        // Given
        List<NotificacionDTO> lista = Arrays.asList(
                notificacionDTO(1L, "usuario1", TipoNotificacion.PRESTAMO_CREADO, false)
        );
        when(notificacionService.obtenerNoLeidas("usuario1")).thenReturn(lista);

        // When & Then
        mockMvc.perform(get("/api/notifications/usuario/usuario1/no-leidas")
                        .header("X-User-Id", "usuario1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk());

        verify(notificacionService).obtenerNoLeidas("usuario1");
    }

    @Test
    void obtenerNoLeidas_cuando_otroUsuario_devuelve403() throws Exception {
        // Given — X-User-Id ("otro") NO coincide con {usuarioId} ("usuario1")

        // When & Then
        mockMvc.perform(get("/api/notifications/usuario/usuario1/no-leidas")
                        .header("X-User-Id", "otro")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(notificacionService, never()).obtenerNoLeidas(anyString());
    }

    @Test
    void obtenerNoLeidas_cuando_admin_devuelve200() throws Exception {
        // Given
        List<NotificacionDTO> lista = Arrays.asList(
                notificacionDTO(1L, "usuario1", TipoNotificacion.PRESTAMO_CREADO, false)
        );
        when(notificacionService.obtenerNoLeidas("usuario1")).thenReturn(lista);

        // When & Then — ROLE_ADMIN puede acceder a cualquier usuarioId
        mockMvc.perform(get("/api/notifications/usuario/usuario1/no-leidas")
                        .header("X-User-Id", "admin")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());

        verify(notificacionService).obtenerNoLeidas("usuario1");
    }

    @Test
    void obtenerNoLeidas_cuando_headerAusente_devuelve403() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/notifications/usuario/usuario1/no-leidas"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(notificacionService, never()).obtenerNoLeidas(anyString());
    }

    // ─── PATCH /api/notifications/usuario/{usuarioId}/leer-todas ──────────

    @Test
    void marcarTodasLeidas_cuando_mismoUsuario_devuelve204() throws Exception {
        // Given
        doNothing().when(notificacionService).marcarTodasLeidas("usuario1");

        // When & Then
        mockMvc.perform(patch("/api/notifications/usuario/usuario1/leer-todas")
                        .header("X-User-Id", "usuario1")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isNoContent());

        verify(notificacionService).marcarTodasLeidas("usuario1");
    }

    @Test
    void marcarTodasLeidas_cuando_otroUsuario_devuelve403() throws Exception {
        // Given — X-User-Id ("otro") NO coincide con {usuarioId} ("usuario1")

        // When & Then
        mockMvc.perform(patch("/api/notifications/usuario/usuario1/leer-todas")
                        .header("X-User-Id", "otro")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(notificacionService, never()).marcarTodasLeidas(anyString());
    }

    @Test
    void marcarTodasLeidas_cuando_admin_devuelve204() throws Exception {
        // Given
        doNothing().when(notificacionService).marcarTodasLeidas("usuario1");

        // When & Then — ROLE_ADMIN puede acceder a cualquier usuarioId
        mockMvc.perform(patch("/api/notifications/usuario/usuario1/leer-todas")
                        .header("X-User-Id", "admin")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isNoContent());

        verify(notificacionService).marcarTodasLeidas("usuario1");
    }

    @Test
    void marcarTodasLeidas_cuando_headerAusente_devuelve403() throws Exception {
        // When & Then
        mockMvc.perform(patch("/api/notifications/usuario/usuario1/leer-todas"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(notificacionService, never()).marcarTodasLeidas(anyString());
    }
}  
