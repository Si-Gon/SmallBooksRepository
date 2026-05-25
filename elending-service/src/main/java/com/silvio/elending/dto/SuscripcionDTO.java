package com.silvio.elending.dto;

import lombok.Data;

// Representa la respuesta de Subscription Service
// Solo los campos que E-Lending necesita para aplicar las reglas del plan
@Data
public class SuscripcionDTO {

    private String usuarioId;
    private String plan;          // "BASICO" o "PREMIUM"
    private Integer maxPrestamos; // 2 para BASICO, 5 para PREMIUM
    private Integer diasPrestamo; // 7 para BASICO, 14 para PREMIUM
    private Boolean activa;
}