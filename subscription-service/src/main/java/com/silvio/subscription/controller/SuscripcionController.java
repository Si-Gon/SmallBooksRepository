package com.silvio.subscription.controller;

import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.dto.SuscripcionResponseDTO;
import com.silvio.subscription.security.JwtExtractor;
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

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(name = "Subscriptions", description = "Gestión de suscripciones — planes BASICO y PREMIUM que determinan límites de préstamo")
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService suscripcionService;
    private final JwtExtractor jwtExtractor;  // inyectado — responsabilidad delegada

    @Operation(summary = "Consultar mi plan actual",
               description = "Devuelve la suscripción activa del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plan obtenido exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "El usuario no tiene suscripción activa")
    })
    @GetMapping("/mi-plan")
    public ResponseEntity<SuscripcionResponseDTO> miPlan(
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {
        String usuarioId = jwtExtractor.extraerUsuario(authHeader);
        SuscripcionResponseDTO dto = suscripcionService.obtenerPorUsuario(usuarioId);

        dto.add(linkTo(methodOn(SuscripcionController.class)
                .miPlan(authHeader)).withSelfRel());
        dto.add(linkTo(methodOn(SuscripcionController.class)
                .cancelar(authHeader)).withRel("cancelar"));

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Obtener plan por usuario",
               description = "Endpoint interno usado por E-Lending Service via Feign")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suscripción obtenida exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "Usuario sin suscripción activa"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<SuscripcionResponseDTO> obtenerPorUsuarioId(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId) {
        SuscripcionResponseDTO dto = suscripcionService.obtenerPorUsuario(usuarioId);

        dto.add(linkTo(methodOn(SuscripcionController.class)
                .obtenerPorUsuarioId(usuarioId)).withSelfRel());

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Crear o cambiar suscripción",
               description = "Crea una nueva suscripción o cambia el plan actual del usuario")
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
        String usuarioId = jwtExtractor.extraerUsuario(authHeader);
        SuscripcionResponseDTO dto = suscripcionService.crear(request, usuarioId);

        dto.add(linkTo(methodOn(SuscripcionController.class)
                .miPlan(authHeader)).withRel("mi-plan"));
        dto.add(linkTo(methodOn(SuscripcionController.class)
                .cancelar(authHeader)).withRel("cancelar"));

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Cancelar suscripción",
               description = "Cancela la suscripción activa — el usuario vuelve al plan BASICO")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suscripción cancelada exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "No hay suscripción activa para cancelar")
    })
    @PatchMapping("/cancelar")
    public ResponseEntity<SuscripcionResponseDTO> cancelar(
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {
        String usuarioId = jwtExtractor.extraerUsuario(authHeader);
        SuscripcionResponseDTO dto = suscripcionService.cancelar(usuarioId);

        dto.add(linkTo(methodOn(SuscripcionController.class)
                .miPlan(authHeader)).withRel("mi-plan"));

        return ResponseEntity.ok(dto);
    }
}