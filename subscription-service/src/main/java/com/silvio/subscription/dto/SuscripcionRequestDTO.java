package com.silvio.subscription.dto;

import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SuscripcionRequestDTO {

    // usuarioId viene del token JWT en el controller
    @NotNull(message = "El plan es obligatorio")
    private PlanSuscripcion plan;

    // Duración en meses — por defecto 1 mes
    private Integer meses = 1;
}