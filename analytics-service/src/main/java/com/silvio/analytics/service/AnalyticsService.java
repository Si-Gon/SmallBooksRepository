package com.silvio.analytics.service;

import com.silvio.analytics.client.LendingClient;
import com.silvio.analytics.dto.EstadisticasDTO;
import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.micrometer.observation.annotation.Observed;
import org.springframework.data.domain.Page;

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
        Page<PrestamoAnalyticsDTO> page = lendingClient.obtenerTodos();
        List<PrestamoAnalyticsDTO> todos = page.getContent();
        log.info("Total préstamos obtenidos para análisis: {} (página {}/{}, total {})",
                todos.size(), page.getNumber(), page.getTotalPages(), page.getTotalElements());

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
        List<PrestamoAnalyticsDTO> historial = lendingClient.obtenerHistorial(usuarioId);
        log.info("Historial del usuario {}: {} préstamos", usuarioId, historial.size());
        return historial;
    }
}