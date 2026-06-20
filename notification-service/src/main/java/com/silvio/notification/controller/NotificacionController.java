package com.silvio.notification.controller;

import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notifications", description = "Gestión de notificaciones — creadas por E-Lending via Feign al crear o vencer préstamos")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @Operation(summary = "Crear notificación",
               description = "Crea una nueva notificación para un usuario. " +
                             "Endpoint interno usado por E-Lending Service via Feign")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Notificación creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de notificación inválidos")
    })
    @PostMapping
    public ResponseEntity<NotificacionDTO> crear(
            @Valid @RequestBody NotificacionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificacionService.crear(request));
    }

    @Operation(summary = "Obtener notificaciones por usuario",
               description = "Devuelve todas las notificaciones (leídas y no leídas) de un usuario")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de notificaciones obtenida"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<NotificacionDTO>> obtenerPorUsuario(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId) {
        return ResponseEntity.ok(notificacionService.obtenerPorUsuario(usuarioId));
    }

    @Operation(summary = "Obtener notificaciones no leídas",
               description = "Devuelve solo las notificaciones pendientes de leer de un usuario")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de notificaciones no leídas"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @GetMapping("/usuario/{usuarioId}/no-leidas")
    public ResponseEntity<List<NotificacionDTO>> obtenerNoLeidas(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId) {
        return ResponseEntity.ok(notificacionService.obtenerNoLeidas(usuarioId));
    }

    @Operation(summary = "Marcar notificación como leída",
               description = "Actualiza el estado de una notificación específica a leída")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notificación marcada como leída"),
        @ApiResponse(responseCode = "404", description = "Notificación no encontrada")
    })
    @PatchMapping("/{id}/leer")
    public ResponseEntity<NotificacionDTO> marcarLeida(
            @Parameter(description = "ID de la notificación", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(notificacionService.marcarLeida(id));
    }

    @Operation(summary = "Marcar todas las notificaciones como leídas",
               description = "Marca todas las notificaciones pendientes de un usuario como leídas de una vez")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Todas las notificaciones marcadas como leídas"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PatchMapping("/usuario/{usuarioId}/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId) {
        notificacionService.marcarTodasLeidas(usuarioId);
        return ResponseEntity.noContent().build();
    }
}