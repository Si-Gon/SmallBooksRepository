package com.silvio.analytics.client;

import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "elending-service")
public interface LendingClient {

    // Historial completo de un usuario
    @GetMapping("/api/lending/prestamos/historial/{usuarioId}")
    List<PrestamoAnalyticsDTO> obtenerHistorial(@PathVariable("usuarioId") String usuarioId);

    // Todos los préstamos — para estadísticas globales
    @GetMapping("/api/lending/prestamos/todos")
    List<PrestamoAnalyticsDTO> obtenerTodos();
}