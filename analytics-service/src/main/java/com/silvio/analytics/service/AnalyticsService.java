package com.silvio.analytics.service;

import com.silvio.analytics.client.LendingClient;
import com.silvio.analytics.dto.EstadisticasDTO;
import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final LendingClient lendingClient;

    // Estadísticas globales del sistema
    
    public EstadisticasDTO obtenerEstadisticas() {
        List<PrestamoAnalyticsDTO> todos;
        try {
            todos = lendingClient.obtenerTodos();
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener datos de préstamos: " + e.getMessage());
        }

        EstadisticasDTO stats = new EstadisticasDTO();

        // Total de préstamos
        stats.setTotalPrestamos((long) todos.size());

        // Activos y vencidos
        stats.setPrestamosActivos(
                todos.stream().filter(p -> "ACTIVO".equals(p.getEstado())).count());
        stats.setPrestamosVencidos(
                todos.stream().filter(p -> "VENCIDO".equals(p.getEstado())).count());

        // Top 5 libros más prestados
        // Agrupa por libroId, cuenta cuántos préstamos tiene cada uno,
        // ordena descendente y toma los primeros 5
        Map<Long, Long> librosMasPrestados = todos.stream()
                .collect(Collectors.groupingBy(
                        PrestamoAnalyticsDTO::getLibroId,
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new));

        stats.setLibrosMasPrestados(librosMasPrestados);

        // Top 5 usuarios más activos
        Map<String, Long> usuariosMasActivos = todos.stream()
                .collect(Collectors.groupingBy(
                        PrestamoAnalyticsDTO::getUsuarioId,
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new));

        stats.setUsuariosMasActivos(usuariosMasActivos);

        return stats;
    }

    // Historial de préstamos de un usuario específico
    
    public List<PrestamoAnalyticsDTO> historialUsuario(String usuarioId) {
        try {
            return lendingClient.obtenerHistorial(usuarioId);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error al obtener historial del usuario: " + usuarioId);
        }
    }
}