package com.silvio.analytics.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

import org.springframework.hateoas.RepresentationModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrestamoAnalyticsDTO extends RepresentationModel<PrestamoAnalyticsDTO> {
    private Long id;
    private String usuarioId;
    private Long libroId;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaVencimiento;
    private String estado; // "ACTIVO" o "VENCIDO"
}