package com.silvio.notification.controller;

import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.exception.AccesoDenegadoException;
import com.silvio.notification.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(name = "Notifications", description = "Gestión de notificaciones — creadas por E-Lending via RabbitMQ al crear o vencer préstamos (endpoint REST disponible para pruebas y uso manual)")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
public class NotificacionController {

    private final NotificacionService notificacionService;

    @Operation(summary = "Crear notificación", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Endpoint REST disponible para pruebas manuales. En producción, las notificaciones se crean vía RabbitMQ desde E-Lending Service.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Notificación creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de notificación inválidos"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @PostMapping
    public ResponseEntity<NotificacionDTO> crear(
            @Valid @RequestBody NotificacionRequestDTO request) {
        NotificacionDTO dto = notificacionService.crear(request);

        dto.add(linkTo(methodOn(NotificacionController.class)
                .obtenerPorUsuario(dto.getUsuarioId(), null, null)).withRel("mis-notificaciones"));
        dto.add(linkTo(methodOn(NotificacionController.class)
                .obtenerNoLeidas(dto.getUsuarioId(), null, null)).withRel("no-leidas"));
        dto.add(linkTo(methodOn(NotificacionController.class)
                .marcarLeida(dto.getId())).withRel("marcar-leida"));

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Obtener notificaciones por usuario", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de notificaciones obtenida"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado — no puedes acceder a notificaciones de otro usuario"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<NotificacionDTO>> obtenerPorUsuario(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId,
            @Parameter(description = "ID del usuario autenticado propagado por el Gateway", required = false)
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @Parameter(description = "Roles del usuario autenticado propagados por el Gateway", required = false)
            @RequestHeader(value = "X-User-Roles", required = false) String currentUserRoles) {
        validarAccesoUsuario(currentUserId, usuarioId, currentUserRoles);

        List<NotificacionDTO> lista = notificacionService.obtenerPorUsuario(usuarioId);

        lista.forEach(dto -> {
            dto.add(linkTo(methodOn(NotificacionController.class)
                    .marcarLeida(dto.getId())).withRel("marcar-leida"));
            dto.add(linkTo(methodOn(NotificacionController.class)
                    .obtenerNoLeidas(usuarioId, null, null)).withRel("no-leidas"));
        });

        return ResponseEntity.ok(lista);
    }

    @Operation(summary = "Obtener notificaciones no leídas", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de notificaciones no leídas"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado — no puedes acceder a notificaciones de otro usuario"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/usuario/{usuarioId}/no-leidas")
    public ResponseEntity<List<NotificacionDTO>> obtenerNoLeidas(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId,
            @Parameter(description = "ID del usuario autenticado propagado por el Gateway", required = false)
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @Parameter(description = "Roles del usuario autenticado propagados por el Gateway", required = false)
            @RequestHeader(value = "X-User-Roles", required = false) String currentUserRoles) {
        validarAccesoUsuario(currentUserId, usuarioId, currentUserRoles);

        List<NotificacionDTO> lista = notificacionService.obtenerNoLeidas(usuarioId);

        lista.forEach(dto ->
            dto.add(linkTo(methodOn(NotificacionController.class)
                    .marcarLeida(dto.getId())).withRel("marcar-leida"))
        );

        return ResponseEntity.ok(lista);
    }

    @Operation(summary = "Marcar notificación como leída", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notificación marcada como leída"),
        @ApiResponse(responseCode = "400", description = "ID de notificación inválido"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "Notificación no encontrada")
    })
    @PatchMapping("/{id}/leer")
    public ResponseEntity<NotificacionDTO> marcarLeida(
            @Parameter(description = "ID de la notificación", required = true)
            @PathVariable @Positive(message = "El ID debe ser un número positivo") Long id) {
        NotificacionDTO dto = notificacionService.marcarLeida(id);

        dto.add(linkTo(methodOn(NotificacionController.class)
                .obtenerPorUsuario(dto.getUsuarioId(), null, null)).withRel("mis-notificaciones"));
        dto.add(linkTo(methodOn(NotificacionController.class)
                .marcarTodasLeidas(dto.getUsuarioId(), null, null)).withRel("marcar-todas-leidas"));

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Marcar todas las notificaciones como leídas", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Todas marcadas como leídas"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado — no puedes acceder a notificaciones de otro usuario"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PatchMapping("/usuario/{usuarioId}/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId,
            @Parameter(description = "ID del usuario autenticado propagado por el Gateway", required = false)
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @Parameter(description = "Roles del usuario autenticado propagados por el Gateway", required = false)
            @RequestHeader(value = "X-User-Roles", required = false) String currentUserRoles) {
        validarAccesoUsuario(currentUserId, usuarioId, currentUserRoles);
        notificacionService.marcarTodasLeidas(usuarioId);
        return ResponseEntity.noContent().build();
    }

    // ─── helper: validación IDOR ────────────────────────────────────────────────

    private void validarAccesoUsuario(String currentUserId, String usuarioId, String currentUserRoles) {
        // 1) header X-User-Id ausente o vacío → denegar
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new AccesoDenegadoException("X-User-Id header es obligatorio");
        }
        // 2) admin bypass — ROLE_ADMIN puede acceder a cualquier usuarioId
        if (currentUserRoles != null && currentUserRoles.contains("ROLE_ADMIN")) {
            return;
        }
        // 3) el usuario autenticado debe coincidir con el usuario solicitado
        if (!currentUserId.equals(usuarioId)) {
            throw new AccesoDenegadoException("Acceso denegado — no puedes acceder a los datos de otro usuario");
        }
        // 4) permitir
    }
}