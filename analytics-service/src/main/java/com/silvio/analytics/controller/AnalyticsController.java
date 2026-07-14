package com.silvio.analytics.controller;

import com.silvio.analytics.dto.EstadisticasDTO;
import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import com.silvio.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(name = "Analytics", description = "Métricas y estadísticas globales de uso de la plataforma SmallBooks")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Obtener estadísticas globales",
               description = "Devuelve métricas generales del sistema: total de préstamos, " +
                              "libros más prestados, usuarios activos y estadísticas por plan")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "503", description = "Error de comunicación con servicios externos"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/estadisticas")
    public ResponseEntity<EstadisticasDTO> obtenerEstadisticas() {
        EstadisticasDTO dto = analyticsService.obtenerEstadisticas();

        dto.add(linkTo(methodOn(AnalyticsController.class)
                .obtenerEstadisticas()).withSelfRel());

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Historial de préstamos por usuario",
               description = "Devuelve el historial completo de préstamos de un usuario específico " +
                              "para análisis de comportamiento de lectura")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial obtenido exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado en el sistema de préstamos"),
        @ApiResponse(responseCode = "503", description = "Error de comunicación con servicios externos"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/historial/{usuarioId}")
    public ResponseEntity<List<PrestamoAnalyticsDTO>> historialUsuario(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId) {
        List<PrestamoAnalyticsDTO> lista = analyticsService.historialUsuario(usuarioId);

        lista.forEach(p ->
            p.add(linkTo(methodOn(AnalyticsController.class).historialUsuario(usuarioId)).withSelfRel()));

        return ResponseEntity.ok(lista);
    }
}