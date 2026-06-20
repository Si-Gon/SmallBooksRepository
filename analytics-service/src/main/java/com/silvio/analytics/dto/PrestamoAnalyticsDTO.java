package com.silvio.analytics.dto;

import lombok.Data;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrestamoAnalyticsDTO {
    private Long id;
    private String usuarioId;
    private Long libroId;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaVencimiento;
    private String estado; // "ACTIVO" o "VENCIDO"
}