package com.silvio.subscription.controller;

import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.dto.SuscripcionResponseDTO;
import com.silvio.subscription.service.SuscripcionService;
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

@Tag(name = "Subscriptions", description = "Gestión de suscripciones — planes BASICO y PREMIUM que determinan límites de préstamo")
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService suscripcionService;

    @Operation(summary = "Consultar mi plan actual",
               description = "Devuelve la suscripción activa del usuario autenticado. " +
                             "El usuario se identifica desde el token JWT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plan obtenido exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "El usuario no tiene suscripción activa")
    })
    @GetMapping("/mi-plan")
    public ResponseEntity<SuscripcionResponseDTO> miPlan(
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {
        String usuarioId = extraerUsuario(authHeader);
        return ResponseEntity.ok(suscripcionService.obtenerPorUsuario(usuarioId));
    }

    @Operation(summary = "Obtener plan por usuario",
               description = "Endpoint interno usado por E-Lending Service via Feign. " +
                             "Consulta el plan de un usuario para validar límites de préstamo " +
                             "(BASICO: 2 préstamos / 7 días — PREMIUM: 5 préstamos / 14 días)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suscripción obtenida exitosamente"),
        @ApiResponse(responseCode = "404", description = "Usuario sin suscripción activa")
    })
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<SuscripcionResponseDTO> obtenerPorUsuarioId(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId) {
        return ResponseEntity.ok(suscripcionService.obtenerPorUsuario(usuarioId));
    }

    @Operation(summary = "Crear o cambiar suscripción",
               description = "Crea una nueva suscripción o cambia el plan actual del usuario. " +
                             "Planes disponibles: BASICO (gratuito) y PREMIUM (pagado)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Suscripción creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Plan inválido o datos incorrectos"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente")
    })
    @PostMapping
    public ResponseEntity<SuscripcionResponseDTO> crear(
            @Valid @RequestBody SuscripcionRequestDTO request,
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {
        String usuarioId = extraerUsuario(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suscripcionService.crear(request, usuarioId));
    }

    @Operation(summary = "Cancelar suscripción",
               description = "Cancela la suscripción activa del usuario autenticado. " +
                             "El usuario vuelve al plan BASICO por defecto")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suscripción cancelada exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "No hay suscripción activa para cancelar")
    })
    @PatchMapping("/cancelar")
    public ResponseEntity<SuscripcionResponseDTO> cancelar(
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {
        String usuarioId = extraerUsuario(authHeader);
        return ResponseEntity.ok(suscripcionService.cancelar(usuarioId));
    }

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