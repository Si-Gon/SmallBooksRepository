package com.silvio.analytics.dto;

import lombok.Data;

import java.util.Map;

@Data
public class EstadisticasDTO {

    // Total de préstamos en el sistema
    private Long totalPrestamos;

    // Préstamos activos actualmente
    private Long prestamosActivos;

    // Préstamos vencidos
    private Long prestamosVencidos;

    // Top 5 libros más prestados: libroId → cantidad de préstamos
    private Map<Long, Long> librosMasPrestados;

    // Top 5 usuarios más activos: usuarioId → cantidad de préstamos
    private Map<String, Long> usuariosMasActivos;
}