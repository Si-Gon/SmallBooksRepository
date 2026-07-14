package com.silvio.analytics.service;

import com.silvio.analytics.client.LendingClient;
import com.silvio.analytics.dto.EstadisticasDTO;
import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import com.silvio.analytics.exception.ErrorDatosPrestamosException;
import com.silvio.analytics.exception.ErrorHistorialUsuarioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final LendingClient lendingClient;

    @Observed(name = "analytics.obtenerEstadisticas")
    public EstadisticasDTO obtenerEstadisticas() {
        log.info("Calculando estadísticas globales del sistema");
        List<PrestamoAnalyticsDTO> todos;
        try {
            todos = lendingClient.obtenerTodos();
            log.info("Total préstamos obtenidos para análisis: {}", todos.size());
        } catch (Exception e) {
            log.error("Error al obtener datos de préstamos: {}", e.getMessage());
            throw new ErrorDatosPrestamosException(e.getMessage());
        }

        EstadisticasDTO stats = new EstadisticasDTO();
        stats.setTotalPrestamos((long) todos.size());

        long activos = todos.stream().filter(p -> "ACTIVO".equals(p.getEstado())).count();
        long vencidos = todos.stream().filter(p -> "VENCIDO".equals(p.getEstado())).count();
        stats.setPrestamosActivos(activos);
        stats.setPrestamosVencidos(vencidos);

        log.info("Estadísticas — total: {}, activos: {}, vencidos: {}",
                todos.size(), activos, vencidos);

        Map<Long, Long> librosMasPrestados = todos.stream()
                .collect(Collectors.groupingBy(
                        PrestamoAnalyticsDTO::getLibroId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, java.util.LinkedHashMap::new));

        stats.setLibrosMasPrestados(librosMasPrestados);

        Map<String, Long> usuariosMasActivos = todos.stream()
                .collect(Collectors.groupingBy(
                        PrestamoAnalyticsDTO::getUsuarioId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, java.util.LinkedHashMap::new));

        stats.setUsuariosMasActivos(usuariosMasActivos);
        log.info("Estadísticas calculadas exitosamente");
        return stats;
    }

    @Observed(name = "analytics.historialUsuario")
    public List<PrestamoAnalyticsDTO> historialUsuario(String usuarioId) {
        log.info("Consultando historial del usuario: {}", usuarioId);
        try {
            List<PrestamoAnalyticsDTO> historial = lendingClient.obtenerHistorial(usuarioId);
            log.info("Historial del usuario {}: {} préstamos", usuarioId, historial.size());
            return historial;
        } catch (Exception e) {
            log.error("Error al obtener historial del usuario {}: {}", usuarioId, e.getMessage());
            throw new ErrorHistorialUsuarioException(usuarioId);
        }
    }
}