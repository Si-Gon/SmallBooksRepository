package com.silvio.analytics.controller;

import com.silvio.analytics.dto.EstadisticasDTO;
import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import com.silvio.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Endpoints:
//   GET /api/analytics/estadisticas              → métricas globales del sistema
//   GET /api/analytics/historial/{usuarioId}     → historial de un usuario

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/estadisticas")
    public ResponseEntity<EstadisticasDTO> obtenerEstadisticas() {
        return ResponseEntity.ok(analyticsService.obtenerEstadisticas());
    }

    @GetMapping("/historial/{usuarioId}")
    public ResponseEntity<List<PrestamoAnalyticsDTO>> historialUsuario(
            @PathVariable String usuarioId) {
        return ResponseEntity.ok(analyticsService.historialUsuario(usuarioId));
    }
}