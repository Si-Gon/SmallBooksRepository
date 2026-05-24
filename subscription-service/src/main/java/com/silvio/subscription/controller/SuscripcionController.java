package com.silvio.subscription.controller;

import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.dto.SuscripcionResponseDTO;
import com.silvio.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Endpoints:
//   GET   /api/subscriptions/mi-plan          → consultar mi suscripción activa
//   POST  /api/subscriptions                  → crear/cambiar suscripción
//   PATCH /api/subscriptions/cancelar         → cancelar suscripción

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/mi-plan")
    public ResponseEntity<SuscripcionResponseDTO> miPlan(
            @RequestHeader("Authorization") String authHeader) {
        String usuarioId = extraerUsuario(authHeader);
        return ResponseEntity.ok(subscriptionService.obtenerPorUsuario(usuarioId));
    }

    @PostMapping
    public ResponseEntity<SuscripcionResponseDTO> crear(
            @Valid @RequestBody SuscripcionRequestDTO request,
            @RequestHeader("Authorization") String authHeader) {
        String usuarioId = extraerUsuario(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.crear(request, usuarioId));
    }

    @PatchMapping("/cancelar")
    public ResponseEntity<SuscripcionResponseDTO> cancelar(
            @RequestHeader("Authorization") String authHeader) {
        String usuarioId = extraerUsuario(authHeader);
        return ResponseEntity.ok(subscriptionService.cancelar(usuarioId));
    }

    // Mismo método que LendingController — extrae username del token JWT
    private String extraerUsuario(String authHeader) {
        try {
            String token = authHeader.substring(7);
            String payload = token.split("\\.")[1];
            String decodedPayload = new String(
                    java.util.Base64.getUrlDecoder().decode(payload));
            return decodedPayload.split("\"sub\":\"")[1].split("\"")[0];
        } catch (Exception e) {
            throw new RuntimeException("No se pudo extraer el usuario del token");
        }
    }
}