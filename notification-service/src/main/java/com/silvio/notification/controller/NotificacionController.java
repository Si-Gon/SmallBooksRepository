package com.silvio.notification.controller;

import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Endpoints:
//   POST  /api/notifications                        → crear (E-Lending via Feign)
//   GET   /api/notifications/usuario/{usuarioId}    → todas las notificaciones
//   GET   /api/notifications/usuario/{usuarioId}/no-leidas → solo no leídas
//   PATCH /api/notifications/{id}/leer             → marcar una como leída
//   PATCH /api/notifications/usuario/{usuarioId}/leer-todas → marcar todas

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<NotificacionDTO> crear(
            @Valid @RequestBody NotificacionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.crear(request));
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<NotificacionDTO>> obtenerPorUsuario(
            @PathVariable String usuarioId) {
        return ResponseEntity.ok(notificationService.obtenerPorUsuario(usuarioId));
    }

    @GetMapping("/usuario/{usuarioId}/no-leidas")
    public ResponseEntity<List<NotificacionDTO>> obtenerNoLeidas(
            @PathVariable String usuarioId) {
        return ResponseEntity.ok(notificationService.obtenerNoLeidas(usuarioId));
    }

    @PatchMapping("/{id}/leer")
    public ResponseEntity<NotificacionDTO> marcarLeida(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.marcarLeida(id));
    }

    @PatchMapping("/usuario/{usuarioId}/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas(@PathVariable String usuarioId) {
        notificationService.marcarTodasLeidas(usuarioId);
        return ResponseEntity.noContent().build();
    }
}