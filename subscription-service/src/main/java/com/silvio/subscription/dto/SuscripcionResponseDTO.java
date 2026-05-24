package com.silvio.subscription.dto;

import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SuscripcionResponseDTO {

    private Long id;
    private String usuarioId;
    private PlanSuscripcion plan;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Boolean activa;

    // Reglas del plan — E-Lending las consulta via Feign
    private Integer maxPrestamos;
    private Integer diasPrestamo;
}