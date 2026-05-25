package com.silvio.notification.controller;

import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.service.NotificacionService;
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
public class NotificacionController {

    private final NotificacionService notificacionService;

    @PostMapping
    public ResponseEntity<NotificacionDTO> crear(
            @Valid @RequestBody NotificacionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificacionService.crear(request));
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<NotificacionDTO>> obtenerPorUsuario(
            @PathVariable String usuarioId) {
        return ResponseEntity.ok(notificacionService.obtenerPorUsuario(usuarioId));
    }

    @GetMapping("/usuario/{usuarioId}/no-leidas")
    public ResponseEntity<List<NotificacionDTO>> obtenerNoLeidas(
            @PathVariable String usuarioId) {
        return ResponseEntity.ok(notificacionService.obtenerNoLeidas(usuarioId));
    }

    @PatchMapping("/{id}/leer")
    public ResponseEntity<NotificacionDTO> marcarLeida(@PathVariable Long id) {
        return ResponseEntity.ok(notificacionService.marcarLeida(id));
    }

    @PatchMapping("/usuario/{usuarioId}/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas(@PathVariable String usuarioId) {
        notificacionService.marcarTodasLeidas(usuarioId);
        return ResponseEntity.noContent().build();
    }
}