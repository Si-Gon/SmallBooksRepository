package com.silvio.analytics.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PrestamoAnalyticsDTO {
    private Long id;
    private String usuarioId;
    private Long libroId;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaVencimiento;
    private String estado; // "ACTIVO" o "VENCIDO"
}